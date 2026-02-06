/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.facets.ldap;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.directory.api.asn1.DecoderException;
import org.apache.directory.api.asn1.ber.Asn1Decoder;
import org.apache.directory.api.asn1.ber.tlv.TLVStateEnum;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.codec.api.LdapApiServiceFactory;
import org.apache.directory.api.ldap.codec.api.LdapMessageContainer;
import org.apache.directory.api.ldap.extras.extended.cancel.CancelRequest;
import org.apache.directory.api.ldap.extras.extended.pwdModify.PasswordModifyRequest;
import org.apache.directory.api.ldap.extras.extended.pwdModify.PasswordModifyResponse;
import org.apache.directory.api.ldap.extras.extended.whoAmI.WhoAmIResponse;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.message.*;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.util.Strings;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.jetbrains.annotations.Nullable;

@ConverterInfo(onlyActivateFor = "ldap")
@Slf4j
public class RbelLdapConverter extends RbelConverterPlugin {

  private static final byte[] LDAP_MESSAGE_PREFIX = {0x30};
  private final LdapApiService ldapApiService = LdapApiServiceFactory.getSingleton();

  @Override
  public void consumeElement(
      final RbelElement rbelElement, final RbelConversionExecutor converter) {
    val potentiallyLdap = rbelElement.getContent().startsWith(LDAP_MESSAGE_PREFIX);
    if (!potentiallyLdap) {
      return;
    }
    try {
      parseLdapMessage(rbelElement, converter);
    } catch (RbelConversionException e) {
      log.debug("Element is not valid LDAP message", e);
    }
  }

  private void parseLdapMessage(final RbelElement element, final RbelConversionExecutor converter) {
    try {
      ASN1Sequence ldapMessageSequence = parseAsn1Sequence(element);
      if (ldapMessageSequence == null) {
        return;
      }
      byte[] actualLdapMessageBytes = ldapMessageSequence.getEncoded();

      Message ldapMessage = parseLdapMessage(element, actualLdapMessageBytes);

      byte[] protocolOpBytes = ldapMessageSequence.getObjectAt(1).toASN1Primitive().getEncoded();
      int protocolOpTag = protocolOpBytes.length > 0 ? Byte.toUnsignedInt(protocolOpBytes[0]) : -1;

      LdapOperationType operationType =
          LdapOperationType.inferFromProtocolOpBytes(ldapMessage, protocolOpBytes);

      val textRepresentationElement =
          wrapValue(
              buildFullMessageDescription(ldapMessage, protocolOpTag, operationType), element);
      val msgIdElement = wrapValue(ldapMessage.getMessageId(), element);
      val protocolOpElement =
          createProtocolOpElement(
              ldapMessage, protocolOpBytes, protocolOpTag, operationType, element);
      val controlsElement = createControlsElement(ldapMessage, element);

      val attributes = extractAttributes(ldapMessage, element, converter);

      val rbelLdapFacet =
          new RbelLdapFacet(
              textRepresentationElement,
              msgIdElement,
              protocolOpElement,
              attributes.orElse(null),
              controlsElement.orElse(null));
      element.addFacet(rbelLdapFacet);

      element.addFacet(new RbelRootFacet<>(rbelLdapFacet));
      handleRequestResponse(element, ldapMessage);

      element.setUsedBytes(actualLdapMessageBytes.length);
    } catch (final DecoderException | IOException e) {
      throw new RbelConversionException("Attempt to parse LDAP failed", e, element, this);
    }
  }

  private Message parseLdapMessage(RbelElement rbelElement, byte[] actualLdapMessageBytes)
      throws DecoderException {
    val container = new LdapMessageContainer<>(ldapApiService);
    ByteBuffer buffer = ByteBuffer.wrap(actualLdapMessageBytes);
    Asn1Decoder.decode(buffer, container);

    if (container.getState() != TLVStateEnum.PDU_DECODED) {
      throw new RbelConversionException("LDAP message not fully decoded", null, rbelElement, this);
    }

    return container.getMessage();
  }

  private static @Nullable ASN1Sequence parseAsn1Sequence(RbelElement rbelElement)
      throws IOException {
    try (ASN1InputStream asn1InputStream =
        new ASN1InputStream(rbelElement.getContent().toInputStream())) {
      var asn1Object = asn1InputStream.readObject();
      if (!(asn1Object instanceof ASN1Sequence sequence)) {
        // Not a valid LDAP message (must be a SEQUENCE)
        return null;
      }
      if (sequence.size() < 2) {
        // LDAP message must have at least messageId and protocolOp
        return null;
      }
      return sequence;
    }
  }

  private RbelElement createProtocolOpElement(
      final Message ldapMessage,
      final byte[] protocolOpBytes,
      final int protocolOpTag,
      LdapOperationType operationType,
      final RbelElement parentElement) {

    String shortDesc = getShortProtocolOpDesc(ldapMessage, protocolOpTag, operationType);
    RbelElement protocolOpElement = RbelElement.wrap(protocolOpBytes, parentElement, shortDesc);

    // Build structured facet
    RbelLdapProtocolOpFacet.RbelLdapProtocolOpFacetBuilder builder =
        RbelLdapProtocolOpFacet.builder();

    // Common handling that applies to most operations
    applyCommonProtocolOpFields(ldapMessage, operationType, protocolOpElement, builder);

    switch (operationType) {
      case MODIFY_DN_REQUEST -> {
        ModifyDnRequest modDnReq = (ModifyDnRequest) ldapMessage;
        builder
            .newRdn(wrapValue(modDnReq.getNewRdn().getName(), protocolOpElement))
            .deleteOldRdn(wrapValue(String.valueOf(modDnReq.getDeleteOldRdn()), protocolOpElement));
        if (modDnReq.getNewSuperior() != null) {
          builder.newSuperior(wrapValue(modDnReq.getNewSuperior().getName(), protocolOpElement));
        }
      }
      case SEARCH_REQUEST -> {
        SearchRequest searchReq = (SearchRequest) ldapMessage;
        builder
            .baseObject(wrapValue(searchReq.getBase().getName(), protocolOpElement))
            .scope(wrapValue(searchReq.getScope().name(), protocolOpElement))
            .derefAliases(wrapValue(searchReq.getDerefAliases().name(), protocolOpElement))
            .sizeLimit(wrapValue(String.valueOf(searchReq.getSizeLimit()), protocolOpElement))
            .timeLimit(wrapValue(String.valueOf(searchReq.getTimeLimit()), protocolOpElement))
            .typesOnly(wrapValue(String.valueOf(searchReq.getTypesOnly()), protocolOpElement))
            .filter(wrapValue(searchReq.getFilter().toString(), protocolOpElement));
      }
      case BIND_REQUEST -> {
        BindRequest bindReq = (BindRequest) ldapMessage;
        int version = bindReq.getVersion3() ? 3 : 2;
        builder
            .version(wrapValue(String.valueOf(version), protocolOpElement))
            .name(wrapValue(bindReq.getName(), protocolOpElement))
            .simple(wrapValue(String.valueOf(bindReq.isSimple()), protocolOpElement));
      }
      case COMPARE_REQUEST -> {
        CompareRequest compareReq = (CompareRequest) ldapMessage;
        builder
            .attributeDesc(wrapValue(compareReq.getAttributeId(), protocolOpElement))
            .assertionValue(
                wrapValue(compareReq.getAssertionValue().getString(), protocolOpElement));
      }
      case PASSWORD_MODIFY_REQUEST -> {
        PasswordModifyRequest pwdModReq = (PasswordModifyRequest) ldapMessage;
        builder
            .userIdentity(wrapValue(pwdModReq.getUserIdentity(), protocolOpElement))
            .oldPassword(wrapValue(pwdModReq.getOldPassword(), protocolOpElement))
            .newPassword(wrapValue(pwdModReq.getNewPassword(), protocolOpElement));
      }
      case CANCEL_REQUEST -> {
        CancelRequest cancelReq = (CancelRequest) ldapMessage;
        builder.cancelId(wrapValue(cancelReq.getCancelId(), protocolOpElement));
      }
      case WHO_AM_I_RESPONSE -> {
        WhoAmIResponse whoAmIResp = (WhoAmIResponse) ldapMessage;
        builder.authzId(wrapValue(whoAmIResp.getAuthzId(), protocolOpElement));
      }
      case PASSWORD_MODIFY_RESPONSE -> {
        PasswordModifyResponse pwdModResp = (PasswordModifyResponse) ldapMessage;
        builder.genPassword(wrapValue(pwdModResp.getGenPassword(), protocolOpElement));
      }
      default -> {}
    }
    builder.operationType(RbelElement.wrap(protocolOpBytes, protocolOpElement, operationType));
    protocolOpElement.addFacet(builder.build());

    return protocolOpElement;
  }

  private void applyCommonProtocolOpFields(
      final Message ldapMessage,
      final LdapOperationType operationType,
      final RbelElement protocolOpElement,
      final RbelLdapProtocolOpFacet.RbelLdapProtocolOpFacetBuilder builder) {

    // Most requests/responses carry a DN in some form
    final String dn = extractDnIfPresent(ldapMessage, operationType);
    if (dn != null) {
      builder.dn(wrapValue(dn, protocolOpElement));
    }

    // Extended request/response naming is uniform
    if (isExtendedRequest(operationType)) {
      builder.requestName(wrapValue(operationType.getExtendedDisplayName(), protocolOpElement));
    }
    if (isExtendedResponse(operationType)) {
      builder.responseName(wrapValue(operationType.getExtendedDisplayName(), protocolOpElement));
    }

    // ResultResponse fields are uniform for most responses
    if (ldapMessage instanceof ResultResponse) {
      if (operationType == LdapOperationType.EXTENDED_RESPONSE
          && ldapMessage instanceof ExtendedResponse extResp2
          && extResp2.getResponseName() != null
          && !extResp2.getResponseName().isBlank()) {
        // keep the more specific responseName if Apache parsed one
        builder.responseName(wrapValue(extResp2.getResponseName(), protocolOpElement));
      }
      addResultResponseFields(ldapMessage, protocolOpElement, builder);
    }
  }

  private boolean isExtendedRequest(LdapOperationType operationType) {
    return switch (operationType) {
      case START_TLS_REQUEST, WHO_AM_I_REQUEST, PASSWORD_MODIFY_REQUEST, CANCEL_REQUEST -> true;
      default -> false;
    };
  }

  private boolean isExtendedResponse(LdapOperationType operationType) {
    return switch (operationType) {
      case START_TLS_RESPONSE, WHO_AM_I_RESPONSE, PASSWORD_MODIFY_RESPONSE, CANCEL_RESPONSE -> true;
      default -> false;
    };
  }

  private @Nullable String extractDnIfPresent(
      Message ldapMessage, LdapOperationType operationType) {
    return switch (operationType) {
      case SEARCH_RESULT_ENTRY -> ((SearchResultEntry) ldapMessage).getObjectName().getName();
      case DELETE_REQUEST -> ((DeleteRequest) ldapMessage).getName().getName();
      case ADD_REQUEST -> ((AddRequest) ldapMessage).getEntryDn().getName();
      case MODIFY_REQUEST -> ((ModifyRequest) ldapMessage).getName().getName();
      case COMPARE_REQUEST -> ((CompareRequest) ldapMessage).getName().getName();
      case MODIFY_DN_REQUEST -> ((ModifyDnRequest) ldapMessage).getName().getName();
      default -> null;
    };
  }

  private static RbelElement wrapValue(Object value, RbelElement parent) {
    return RbelElement.wrap(null, parent, value);
  }

  private String getShortProtocolOpDesc(
      final Message ldapMessage, int protocolOpTag, LdapOperationType operationType) {
    switch (operationType) {
      case SEARCH_RESULT_ENTRY -> {
        SearchResultEntry entry = (SearchResultEntry) ldapMessage;
        return operationType + "(dn='" + entry.getObjectName().getName() + "')";
      }
      case DELETE_REQUEST -> {
        DeleteRequest deleteReq = (DeleteRequest) ldapMessage;
        return operationType + "(dn='" + deleteReq.getName().getName() + "')";
      }
      case ADD_REQUEST -> {
        AddRequest addReq = (AddRequest) ldapMessage;
        return operationType + "(dn='" + addReq.getEntryDn().getName() + "')";
      }
      case MODIFY_REQUEST -> {
        ModifyRequest modReq = (ModifyRequest) ldapMessage;
        return operationType + "(dn='" + modReq.getName().getName() + "')";
      }
      case MODIFY_DN_REQUEST -> {
        ModifyDnRequest modDnReq = (ModifyDnRequest) ldapMessage;
        return operationType + "(dn='" + modDnReq.getName().getName() + "')";
      }
      case SEARCH_REQUEST -> {
        SearchRequest searchReq = (SearchRequest) ldapMessage;
        String baseDn = Optional.ofNullable(searchReq.getBase()).map(Dn::getName).orElse("");
        String attributes =
            searchReq.getAttributes().isEmpty() ? "" : String.join(", ", searchReq.getAttributes());
        return operationType
            + "(baseDN='"
            + baseDn
            + "', scope='"
            + describeScope(searchReq.getScope())
            + "', derefPolicy='"
            + describeDerefPolicy(searchReq.getDerefAliases())
            + "', sizeLimit="
            + searchReq.getSizeLimit()
            + ", timeLimit="
            + searchReq.getTimeLimit()
            + ", typesOnly="
            + searchReq.getTypesOnly()
            + ", filter='"
            + searchReq.getFilter()
            + "', attributes={"
            + attributes
            + "})";
      }
      case BIND_REQUEST -> {
        BindRequest bindReq = (BindRequest) ldapMessage;
        int version = bindReq.getVersion3() ? 3 : 2;
        return operationType
            + "(version="
            + version
            + ", bindDN='"
            + Optional.ofNullable(bindReq.getName()).orElse("")
            + "', type="
            + (bindReq.isSimple() ? "simple" : "sasl")
            + ")";
      }
      case COMPARE_REQUEST -> {
        CompareRequest compareReq = (CompareRequest) ldapMessage;
        return operationType + "(dn='" + compareReq.getName().getName() + "')";
      }
      case EXTENDED_REQUEST,
          WHO_AM_I_REQUEST,
          CANCEL_REQUEST,
          PASSWORD_MODIFY_REQUEST,
          START_TLS_REQUEST -> {
        ExtendedRequest extReq = (ExtendedRequest) ldapMessage;
        return operationType + "(name='" + extReq.getRequestName() + "')";
      }
      case WHO_AM_I_RESPONSE, CANCEL_RESPONSE, PASSWORD_MODIFY_RESPONSE, START_TLS_RESPONSE -> {
        ExtendedResponse extResp = (ExtendedResponse) ldapMessage;
        return operationType + "(oid='" + extResp.getResponseName() + "')";
      }
      case DELETE_RESPONSE,
          ADD_RESPONSE,
          MODIFY_RESPONSE,
          MODIFY_DN_RESPONSE,
          SEARCH_RESULT_DONE,
          BIND_RESPONSE,
          COMPARE_RESPONSE,
          EXTENDED_RESPONSE -> {
        if (!(ldapMessage instanceof ResultResponse resultResp)) {
          // Defensive fallback: inference/tag said "response" but Apache decoded a request (or
          // other)
          return operationType + "(type=" + formatProtocolOpTag(protocolOpTag) + ")";
        }
        String resultCode =
            Optional.ofNullable(resultResp.getLdapResult())
                .map(LdapResult::getResultCode)
                .map(code -> Integer.toString(code.getResultCode()))
                .orElse("unknown");
        return operationType
            + "(type="
            + formatProtocolOpTag(protocolOpTag)
            + ", resultCode="
            + resultCode
            + ")";
      }

      default -> {
        return operationType.toString();
      }
    }
  }

  private String buildFullMessageDescription(
      final Message ldapMessage, int protocolOpTag, LdapOperationType operationType) {
    StringBuilder sb = new StringBuilder();
    sb.append("LDAPMessage(msgID=").append(ldapMessage.getMessageId());
    sb.append(", protocolOp=")
        .append(getShortProtocolOpDesc(ldapMessage, protocolOpTag, operationType));

    if (ldapMessage instanceof SearchResultEntry searchResultEntry) {
      String attributesDescription = formatAttributes(searchResultEntry);
      if (!attributesDescription.isEmpty()) {
        sb.append(", attrs={").append(attributesDescription).append("}");
      }
    }

    Map<String, Control> controls = ldapMessage.getControls();
    if (controls != null && !controls.isEmpty()) {
      sb.append(", controls={");
      sb.append(
          controls.values().stream().map(this::formatControl).collect(Collectors.joining(", ")));
      sb.append("}");
    }

    sb.append(")");
    return sb.toString();
  }

  private String formatAttributes(SearchResultEntry searchResultEntry) {
    StringBuilder sb = new StringBuilder();
    boolean firstAttribute = true;
    Entry entry = searchResultEntry.getEntry();
    for (Attribute attribute : entry.getAttributes()) {
      if (!firstAttribute) {
        sb.append(", ");
      }
      firstAttribute = false;
      sb.append("Attribute(name=").append(attribute.getUpId()).append(", values={");
      boolean firstValue = true;
      for (Value value : attribute) {
        if (!firstValue) {
          sb.append(", ");
        }
        firstValue = false;
        sb.append(formatAttributeValue(value));
      }
      sb.append("})");
    }
    return sb.toString();
  }

  private String formatAttributeValue(Value value) {
    if (value.isHumanReadable()) {
      return "'" + value.getString() + "'";
    }
    byte[] bytes = value.getBytes();
    if (bytes == null) {
      return "{null}";
    }
    return "'" + Base64.getEncoder().encodeToString(bytes) + "'";
  }

  private String formatControl(Control control) {
    StringBuilder sb = new StringBuilder();
    sb.append("Control(oid=").append(control.getOid());
    sb.append(", isCritical=").append(control.isCritical());
    // Try to get the value if available
    if (control
        instanceof
        org.apache.directory.api.ldap.model.message.controls.OpaqueControl opaqueControl) {
      byte[] value = opaqueControl.getEncodedValue();
      if (value == null) {
        sb.append(", value={null}");
      } else {
        sb.append(", value={").append(Strings.dumpBytes(value)).append("}");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  private void handleRequestResponse(RbelElement rbelElement, Message ldapMessage) {
    val messageTypeName = ldapMessage.getType().name();
    if (ldapMessage instanceof Request) {
      rbelElement.addFacet(new RbelRequestFacet(messageTypeName, false));
    } else {
      rbelElement.addFacet(new RbelResponseFacet(messageTypeName));
    }
  }

  private Optional<RbelElement> extractAttributes(
      final Message ldapMessage,
      final RbelElement parentElement,
      final RbelConversionExecutor converter) {

    if (ldapMessage instanceof SearchResultEntry searchResultEntry) {
      return Optional.of(
          convertEntryToAttributesElement(searchResultEntry.getEntry(), parentElement, converter));
    }
    if (ldapMessage instanceof AddRequest addRequest) {
      return Optional.of(
          convertEntryToAttributesElement(addRequest.getEntry(), parentElement, converter));
    }
    if (ldapMessage instanceof ModifyRequest modifyRequest) {
      val modifications = modifyRequest.getModifications();
      val result = new RbelElement(null, parentElement);
      for (Modification mod : modifications) {
        List<RbelElement> valueElements = new java.util.ArrayList<>();
        for (Value value : mod.getAttribute()) {
          RbelElement valueElement = convertAttribute(converter, value, result);
          valueElements.add(valueElement);
        }
        result.addFacet(
            new RbelLdapModificationFacet(
                wrapValue(mod.getOperation().name(), result),
                wrapValue(mod.getAttribute().getUpId(), result),
                valueElements));
      }
      return Optional.of(result);
    }
    return Optional.empty();
  }

  private RbelElement convertEntryToAttributesElement(
      Entry entry, RbelElement parentElement, RbelConversionExecutor converter) {
    val attributesFacet = new RbelLdapAttributesFacet();
    val result = new RbelElement(null, parentElement);

    for (final Attribute attr : entry.getAttributes()) {
      final String originalName = attr.getUpId();
      final String sanitizedName = sanitizeAttributeName(originalName);
      for (final Value value : attr) {
        RbelElement attributeValue = convertAttribute(converter, value, result);
        attributeValue.addFacet(new RbelLdapAttributeMetadataFacet(originalName));
        attributesFacet.put(sanitizedName, attributeValue);
      }
    }
    result.addFacet(attributesFacet);
    return result;
  }

  private static RbelElement convertAttribute(
      RbelConversionExecutor converter, Value value, RbelElement result) {
    return value.isHumanReadable()
        ? converter.convertElement(value.getString(), result)
        : converter.convertElement(value.getBytes(), result);
  }

  private Optional<RbelElement> createControlsElement(
      Message ldapMessage, RbelElement parentElement) {
    Map<String, Control> controls = ldapMessage.getControls();
    if (controls == null || controls.isEmpty()) {
      return Optional.empty();
    }
    RbelElement controlsElement = new RbelElement(null, parentElement);
    RbelLdapControlsFacet controlsFacet = new RbelLdapControlsFacet();
    controls.forEach(
        (oid, control) ->
            controlsFacet.put(
                oid,
                wrapValue(
                    String.format(
                        "oid=%s, isCritical=%s, value=%s",
                        oid,
                        control.isCritical(),
                        control
                                instanceof
                                org.apache.directory.api.ldap.model.message.controls.OpaqueControl
                                    opaqueControl
                            ? describeControlValue(opaqueControl)
                            : "{unsupported}"),
                    controlsElement)));
    controlsElement.addFacet(controlsFacet);
    return Optional.of(controlsElement);
  }

  private String describeControlValue(
      org.apache.directory.api.ldap.model.message.controls.OpaqueControl control) {
    byte[] value = control.getEncodedValue();
    if (value == null) {
      return "{null}";
    }
    return "{" + Strings.dumpBytes(value) + "}";
  }

  private String sanitizeAttributeName(String attributeName) {
    if (attributeName == null || attributeName.isBlank()) {
      return "attribute";
    }
    String sanitized = attributeName.replaceAll("[^A-Za-z0-9_]", "_");
    if (sanitized.isBlank()) {
      sanitized = "attribute";
    }
    if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
      sanitized = "attr_" + sanitized;
    }
    return sanitized;
  }

  private String describeScope(SearchScope scope) {
    return switch (scope) {
      case OBJECT -> "BASE";
      case ONELEVEL -> "ONE";
      case SUBTREE -> "SUBTREE";
    };
  }

  private String describeDerefPolicy(AliasDerefMode policy) {
    if (policy == null) {
      return "UNKNOWN";
    }
    String name = policy.name();
    return name.startsWith("DEREF_") ? name.substring("DEREF_".length()) : name;
  }

  private String formatProtocolOpTag(int protocolOpTag) {
    if (protocolOpTag < 0) {
      return "UNKNOWN";
    }
    return Integer.toString(protocolOpTag);
  }

  private void addResultResponseFields(
      final Message ldapMessage,
      final RbelElement protocolOpElement,
      final RbelLdapProtocolOpFacet.RbelLdapProtocolOpFacetBuilder builder) {
    if (ldapMessage instanceof ResultResponse resultResp) {
      LdapResult ldapResult = resultResp.getLdapResult();
      builder
          .resultCode(wrapValue(ldapResult.getResultCode().name(), protocolOpElement))
          .matchedDN(wrapValue(ldapResult.getMatchedDn().getName(), protocolOpElement))
          .diagnosticMessage(wrapValue(ldapResult.getDiagnosticMessage(), protocolOpElement));
    }
  }
}
