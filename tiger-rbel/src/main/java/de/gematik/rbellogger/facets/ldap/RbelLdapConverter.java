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

import com.unboundid.asn1.ASN1Element;
import com.unboundid.asn1.ASN1Exception;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.SearchResultEntryProtocolOp;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPException;
import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.asn1.ASN1InputStream;

@ConverterInfo(onlyActivateFor = "ldap")
@Slf4j
public class RbelLdapConverter extends RbelConverterPlugin {

  private final byte[] ldapMessagePrefix = {0x30};

  @Override
  public void consumeElement(
      final RbelElement rbelElement, final RbelConversionExecutor converter) {
    val potentiallyLdap = rbelElement.getContent().startsWith(ldapMessagePrefix);
    if (!potentiallyLdap) {
      return;
    }
    parseLdapMessage(rbelElement, converter);
  }

  private void parseLdapMessage(
      final RbelElement rbelElement, final RbelConversionExecutor converter) {
    try {
      val asn1InputStream =
          new ASN1InputStream(new ByteArrayInputStream(rbelElement.getRawContent()));
      val data = asn1InputStream.readObject().getEncoded();

      val asn1Element = ASN1Element.decode(data);
      val ldapMessage = LDAPMessage.decode(asn1Element);

      val textRepresentationElement =
          extractBy(LDAPMessage::toString, ldapMessage, rbelElement, converter);
      val msgIdElement =
          extractBy(m -> Integer.toString(m.getMessageID()), ldapMessage, rbelElement, converter);
      val protocolOpElement =
          extractBy(
              m -> m.getProtocolOp().getClass().getSimpleName(),
              ldapMessage,
              rbelElement,
              converter);

      val attributes = extractAttributes(ldapMessage, rbelElement, converter);

      val rbelLdapFacet =
          new RbelLdapFacet(
              textRepresentationElement, msgIdElement, protocolOpElement, attributes.orElse(null));
      rbelElement.addFacet(rbelLdapFacet);

      rbelElement.addFacet(new RbelRootFacet<>(rbelLdapFacet));
      handleRequestResponse(rbelElement, ldapMessage);
    } catch (final ASN1Exception | LDAPException | IOException e) {
      throw new RbelConversionException("Attempt to parse LDAP failed", e, rbelElement, this);
    }
  }

  private void handleRequestResponse(RbelElement rbelElement, LDAPMessage ldapMessage) {
    val protocolOp = ldapMessage.getProtocolOp().getClass().getSimpleName();
    if (protocolOp.contains("Request")) {
      rbelElement.addFacet(new RbelRequestFacet(protocolOp, false));
    } else {
      rbelElement.addFacet(new RbelResponseFacet(protocolOp));
    }
  }

  private RbelElement extractBy(
      final Function<LDAPMessage, String> extract,
      final LDAPMessage ldapMessage,
      final RbelElement parentElement,
      final RbelConversionExecutor converter) {

    val value = extract.apply(ldapMessage);
    return converter.convertElement(value, parentElement);
  }

  private Optional<RbelElement> extractAttributes(
      final LDAPMessage ldapMessage,
      final RbelElement parentElement,
      final RbelConversionExecutor converter) {
    val attributesFacet = new RbelLdapAttributesFacet();

    if (ldapMessage.getProtocolOp() instanceof SearchResultEntryProtocolOp) {
      final SearchResultEntryProtocolOp op = ldapMessage.getSearchResultEntryProtocolOp();
      val result = new RbelElement(new byte[] {}, parentElement);

      for (final Attribute attr : op.getAttributes()) {
        for (final String value : attr.getValues()) {
          attributesFacet.put(attr.getName(), converter.convertElement(value, result));
        }
      }
      result.addFacet(attributesFacet);
      return Optional.of(result);
    }

    return Optional.empty();
  }
}
