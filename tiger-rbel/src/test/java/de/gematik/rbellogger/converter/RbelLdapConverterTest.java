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
package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.facets.ldap.LdapOperationType;
import de.gematik.rbellogger.facets.ldap.RbelLdapAttributeMetadataFacet;
import de.gematik.rbellogger.facets.ldap.RbelLdapAttributesFacet;
import de.gematik.rbellogger.facets.ldap.RbelLdapFacet;
import de.gematik.rbellogger.facets.ldap.RbelLdapModificationFacet;
import de.gematik.rbellogger.facets.ldap.RbelLdapProtocolOpFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.directory.api.asn1.util.Asn1Buffer;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.codec.api.LdapApiServiceFactory;
import org.apache.directory.api.ldap.codec.api.LdapEncoder;
import org.apache.directory.api.ldap.codec.controls.OpaqueControlFactory;
import org.apache.directory.api.ldap.extras.extended.ads_impl.cancel.CancelFactory;
import org.apache.directory.api.ldap.extras.extended.cancel.CancelRequestImpl;
import org.apache.directory.api.ldap.extras.extended.pwdModify.PasswordModifyRequestImpl;
import org.apache.directory.api.ldap.extras.extended.startTls.StartTlsRequestImpl;
import org.apache.directory.api.ldap.extras.extended.startTls.StartTlsResponseImpl;
import org.apache.directory.api.ldap.extras.extended.whoAmI.WhoAmIRequestImpl;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.message.AbandonRequestImpl;
import org.apache.directory.api.ldap.model.message.AddRequestImpl;
import org.apache.directory.api.ldap.model.message.AliasDerefMode;
import org.apache.directory.api.ldap.model.message.BindRequestImpl;
import org.apache.directory.api.ldap.model.message.BindResponseImpl;
import org.apache.directory.api.ldap.model.message.CompareRequestImpl;
import org.apache.directory.api.ldap.model.message.CompareResponseImpl;
import org.apache.directory.api.ldap.model.message.DeleteRequestImpl;
import org.apache.directory.api.ldap.model.message.ModifyDnRequestImpl;
import org.apache.directory.api.ldap.model.message.ModifyDnResponseImpl;
import org.apache.directory.api.ldap.model.message.ModifyRequestImpl;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchResultDoneImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.message.UnbindRequestImpl;
import org.apache.directory.api.ldap.model.message.controls.OpaqueControl;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.util.FileUtils;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.junit.jupiter.api.Test;

class RbelLdapConverterTest {

  //  30 35 -- Begin the LDAPMessage sequence
  //     02 01 05 -- The message ID (integer value 5)
  //     4a 11 64 63 3d 65 78 61 6d 70 -- The delete request protocol op
  //           6c 65 2c 64 63 3d 63 6f -- (octet string
  //           6d                      -- dc=example,dc=com)
  //     a0 1d -- Begin the sequence for the set of controls
  //        30 1b -- Begin the sequence for the first control
  //           04 16 31 2e 32 2e 38 34 30 2e -- The control OID
  //   31 31 33 35 35 36 2e 31 -- (octet string
  //   2e 34 2e 38 30 35       -- 1.2.840.113556.1.4.805)
  //           01 01 ff -- The control criticality (Boolean true)
  static final byte[] DELETE_REQUEST =
      HexFormat.of()
          .parseHex(
              "30350201054a1164633d6578616d706c652c64633d636f6da01d301b0416312e322e3834302e3131333535362e312e342e3830350101ff");

  // 30 49 -- Begin the LDAPMessage sequence
  //    02 01 02 -- The message ID (integer value 2)
  //    64 44 -- Begin the search result entry protocol op
  //       04 11 64 63 3d 65 78 61 6d 70 -- The entry DN
  //             6c 65 2c 64 63 3d 63 6f -- (octet string "dc=example,dc=com")
  //             6d
  //       30 2f -- Begin the sequence of attributes
  //          30 1c -- Begin the first attribute sequence
  //             04 0b 6f 62 6a 65 63 74 43 6c -- The attribute description
  //     61 73 73                -- (octet string "objectClass")
  //             31 0d -- Begin the set of objectClass values
  //  04 03 74 6f 70 -- The first value (octet string "top")
  //  04 06 64 6f 6d 61 69 6e -- The second value (octet string "domain")
  //          30 0f -- Begin the second attribute sequence
  //             04 02 64 63 -- The attribute description (octet string "dc")
  //             31 09 -- Begin the set of dc values
  //  04 07 65 78 61 6d 70 6c 65 -- The value (octet string "example")
  static final byte[] SEARCH_RESPONSE = {
    0x30, 0x49, 0x02, 0x01, 0x02, 0x64, 0x44, 0x04, 0x11, 0x64, 0x63, 0x3d, 0x65,
    0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2c, 0x64, 0x63, 0x3d, 0x63, 0x6f, 0x6d,
    0x30, 0x2f, 0x30, 0x1c, 0x04, 0x0b, 0x6f, 0x62, 0x6a, 0x65, 0x63, 0x74, 0x43,
    0x6c, 0x61, 0x73, 0x73, 0x31, 0x0d, 0x04, 0x03, 0x74, 0x6f, 0x70, 0x04, 0x06,
    0x64, 0x6f, 0x6d, 0x61, 0x69, 0x6e, 0x30, 0x0f, 0x04, 0x02, 0x64, 0x63, 0x31,
    0x09, 0x04, 0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65
  };

  static final byte[] INVALID_LDAP_MESSAGE = {0x30, 0x42};

  private final RbelConfiguration config = new RbelConfiguration().activateConversionFor("ldap");
  private final RbelConverter rbelConverter = RbelLogger.build(config).getRbelConverter();

  @Test
  void convertMessage_shouldConvertCorrectRequest() {
    final RbelElement convertedElement = rbelConverter.convertElement(DELETE_REQUEST, null);
    final String expectedTextRepresentation =
        "LDAPMessage(msgID=5, protocolOp=DeleteRequest(dn='dc=example,dc=com'),"
            + " controls={Control(oid=1.2.840.113556.1.4.805, isCritical=true)})";

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .hasFacet(RbelRequestFacet.class)
        .doesNotHaveChildWithPath("$.attributes")
        .extractChildWithPath("$.textRepresentation")
        .hasValueEqualTo(expectedTextRepresentation)
        .andTheInitialElement()
        .extractChildWithPath("$.msgId")
        .hasValueEqualTo(5)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp")
        .hasFacet(RbelLdapProtocolOpFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.dn")
        .hasValueEqualTo("dc=example,dc=com")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.DELETE_REQUEST)
        .andTheInitialElement()
        .extractChildWithPath("$.controls.oid_1_2_840_113556_1_4_805")
        .hasValueEqualTo("oid=1.2.840.113556.1.4.805, isCritical=true, value={unsupported}");
  }

  @Test
  void convertMessage_shouldConvertCorrectResponse() {
    final RbelElement convertedElement = rbelConverter.convertElement(SEARCH_RESPONSE, null);
    final String expectedTextRepresentation =
        "LDAPMessage(msgID=2, "
            + "protocolOp=SearchResultEntry(dn='dc=example,dc=com'), "
            + "attrs={Attribute(name=objectClass, values={'top', 'domain'}),"
            + " Attribute(name=dc, values={'example'})})";

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .hasFacet(RbelResponseFacet.class)
        .extractChildWithPath("$.textRepresentation")
        .hasValueEqualTo(expectedTextRepresentation)
        .andTheInitialElement()
        .extractChildWithPath("$.msgId")
        .hasValueEqualTo(2)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp")
        .hasFacet(RbelLdapProtocolOpFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.dn")
        .hasValueEqualTo("dc=example,dc=com")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.SEARCH_RESULT_ENTRY)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes")
        .hasFacet(RbelLdapAttributesFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.objectClass[0]")
        .hasStringContentEqualTo("top")
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.objectClass[1]")
        .hasStringContentEqualTo("domain")
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.dc")
        .hasStringContentEqualTo("example");
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertCorrectResponseWithSuffix() {
    final ObjectMapper jsonMapper =
        new ObjectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    final Map<String, String> test_case =
        jsonMapper.readValue(
            FileUtils.readFileToByteArray(
                new File("src/test/resources/ldap/ldapMessageWithSuffix.json")),
            new TypeReference<>() {});

    final byte[] messageWithSuffix = HexFormat.of().parseHex(test_case.get("message"));
    final String expectedLdapMessage = test_case.get("expected");

    final RbelElement convertedElement = rbelConverter.convertElement(messageWithSuffix, null);

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .hasFacet(RbelResponseFacet.class)
        .extractChildWithPath("$.textRepresentation")
        .hasValueEqualTo(expectedLdapMessage)
        .andTheInitialElement()
        .extractChildWithPath("$.msgId")
        .hasValueEqualTo(1)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp")
        .hasValueEqualTo(
            "SearchResultEntry(dn='uid=9489c6df-f805-44a2-9b3b-37aaac06840a,dc=data,dc=vzd')")
        .andTheInitialElement()
        .extractChildWithPath("$.attributes")
        .hasFacet(RbelLdapAttributesFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.telematikid")
        .hasStringContentEqualTo("5-SMC-B-Testkarte-883110000129221")
        .andTheInitialElement()
        .hasChildWithPath("$.attributes.usercertificate_binary");
  }

  @Test
  void convertMessage_shouldIgnoreInvalidLdapMessage() {
    final RbelElement convertedElement = rbelConverter.convertElement(INVALID_LDAP_MESSAGE, null);

    assertThat(convertedElement.hasFacet(RbelLdapFacet.class)).isFalse();
    assertThat(convertedElement.findElement("$.textRepresentation")).isEmpty();
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldHandleAllKimTestCases() {
    final ObjectMapper jsonMapper =
        new ObjectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    final Map<String, String> test_cases =
        jsonMapper.readValue(
            FileUtils.readFileToByteArray(new File("src/test/resources/ldap/ldapMessages.json")),
            new TypeReference<>() {});

    for (Map.Entry<String, String> entry : test_cases.entrySet()) {
      final byte[] message = HexFormat.of().parseHex(entry.getKey());
      final RbelElement convertedElement = rbelConverter.convertElement(message, null);

      assertThat(convertedElement)
          .hasFacet(RbelLdapFacet.class)
          .extractChildWithPath("$.textRepresentation")
          .hasValueEqualTo(entry.getValue());
    }
  }

  @Test
  void shouldRenderCleanHtmlIncludingAttributes() {
    final RbelElement convertedElement = rbelConverter.convertElement(SEARCH_RESPONSE, null);
    final String html = RbelHtmlRenderer.render(List.of(convertedElement));

    assertThat(html)
        .contains("SearchResultEntry")
        .contains("dc=example,dc=com")
        .containsIgnoringCase("objectClass")
        .contains("top")
        .contains("domain");
  }

  @Test
  void shouldRenderCleanHtmlWithoutAttributes() {
    final RbelElement convertedElement = rbelConverter.convertElement(DELETE_REQUEST, null);
    final String html = RbelHtmlRenderer.render(List.of(convertedElement));

    assertThat(html).contains("DeleteRequest").contains("dc=example,dc=com");
  }

  @SneakyThrows
  @Test
  void shouldRenderMessageIdCorrectly() {
    var searchReq = new SearchRequestImpl();
    searchReq.setMessageId(42);
    searchReq.setBase(new Dn("dc=example,dc=com"));
    searchReq.setScope(SearchScope.SUBTREE);
    searchReq.setFilter("(objectClass=*)");

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, searchReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    final String html = RbelHtmlRenderer.render(List.of(elem));

    assertThat(html).contains("Message ID").contains(">42<").doesNotContain(">null<");
  }

  @SneakyThrows
  @Test
  void shouldRenderSearchRequestWithRequestedAttributesList() {
    var searchReq = new SearchRequestImpl();
    searchReq.setMessageId(100);
    searchReq.setBase(new Dn("dc=example,dc=com"));
    searchReq.setScope(SearchScope.SUBTREE);
    searchReq.setFilter("(objectClass=*)");
    searchReq.addAttributes("cn", "mail", "telephoneNumber");

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, searchReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    final String html = RbelHtmlRenderer.render(List.of(elem));

    assertThat(html)
        .contains("SearchRequest")
        .contains("Requested Attributes")
        .contains("<li>cn</li>")
        .contains("<li>mail</li>")
        .contains("<li>telephoneNumber</li>");
  }

  @SneakyThrows
  @Test
  void shouldRenderSaslBindRequest() {
    var bindReq = new BindRequestImpl();
    bindReq.setMessageId(51);
    bindReq.setVersion3(true);
    bindReq.setSimple(false);
    bindReq.setSaslMechanism("GSSAPI");
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, bindReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    final String html = RbelHtmlRenderer.render(List.of(elem));

    assertThat(html).contains("BindRequest").contains("SASL Mechanism").contains("GSSAPI");
  }

  @SneakyThrows
  @Test
  void shouldRenderExtendedRequestWithOid() {
    // Use a known-working extended request (StartTLS)
    var startTlsReq = new StartTlsRequestImpl();
    startTlsReq.setMessageId(52);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, startTlsReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    final String html = RbelHtmlRenderer.render(List.of(elem));

    assertThat(html).contains("StartTlsRequest").contains("Request Name").contains("StartTLS");
  }

  @Test
  void protocolOp_shouldProvideStructuredAccessToDeleteRequest() {
    final RbelElement convertedElement = rbelConverter.convertElement(DELETE_REQUEST, null);

    assertThat(convertedElement)
        .extractChildWithPath("$.protocolOp")
        .hasFacet(RbelLdapProtocolOpFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.dn")
        .hasValueEqualTo("dc=example,dc=com");

    // Verify the operation type is available as an element
    assertThat(convertedElement)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.DELETE_REQUEST);
  }

  @Test
  void protocolOp_shouldProvideStructuredAccessToSearchResultEntry() {
    final RbelElement convertedElement = rbelConverter.convertElement(SEARCH_RESPONSE, null);

    assertThat(convertedElement)
        .extractChildWithPath("$.protocolOp")
        .hasFacet(RbelLdapProtocolOpFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.dn")
        .hasValueEqualTo("dc=example,dc=com");

    // Verify the operation type is available as an element
    assertThat(convertedElement)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.SEARCH_RESULT_ENTRY);
  }

  @SneakyThrows
  @Test
  void protocolOp_shouldProvideAttributesForSearchRequest() {
    var searchReq = new SearchRequestImpl();
    searchReq.setMessageId(100);
    searchReq.setBase(new Dn("dc=example,dc=com"));
    searchReq.setScope(SearchScope.SUBTREE);
    searchReq.setFilter("(objectClass=*)");
    searchReq.addAttributes("cn", "mail", "telephoneNumber");

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, searchReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp")
        .hasFacet(RbelLdapProtocolOpFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.SEARCH_REQUEST)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.baseObject")
        .hasValueEqualTo("dc=example,dc=com")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.scope")
        .hasValueEqualTo("SUBTREE")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.filter")
        .hasValueEqualTo("(objectClass=*)")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.requestedAttributes.0")
        .hasValueEqualTo("cn")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.requestedAttributes.1")
        .hasValueEqualTo("mail")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.requestedAttributes.2")
        .hasValueEqualTo("telephoneNumber");
  }

  @SneakyThrows
  @Test
  void protocolOp_shouldProvideAllSearchRequestFields() {
    var searchReq = new SearchRequestImpl();
    searchReq.setMessageId(101);
    searchReq.setBase(new Dn("dc=test,dc=com"));
    searchReq.setScope(SearchScope.ONELEVEL);
    searchReq.setDerefAliases(AliasDerefMode.DEREF_ALWAYS);
    searchReq.setSizeLimit(100);
    searchReq.setTimeLimit(60);
    searchReq.setTypesOnly(true);
    searchReq.setFilter("(cn=test)");

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, searchReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .extractChildWithPath("$.protocolOp.derefAliases")
        .hasValueEqualTo("DEREF_ALWAYS")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.sizeLimit")
        .hasValueEqualTo("100")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.timeLimit")
        .hasValueEqualTo("60")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.typesOnly")
        .hasValueEqualTo("true");
  }

  @SneakyThrows
  @Test
  void protocolOp_shouldProvideModifyDnRequestFields() {
    var modDnReq = new ModifyDnRequestImpl();
    modDnReq.setMessageId(102);
    modDnReq.setName(new Dn("cn=Old Name,dc=example,dc=com"));
    modDnReq.setNewRdn(new Rdn("cn=New Name"));
    modDnReq.setDeleteOldRdn(true);
    modDnReq.setNewSuperior(new Dn("ou=People,dc=example,dc=com"));

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, modDnReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .extractChildWithPath("$.protocolOp.dn")
        .hasValueEqualTo("cn=Old Name,dc=example,dc=com")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.newRdn")
        .hasValueEqualTo("cn=New Name")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.deleteOldRdn")
        .hasValueEqualTo("true")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.newSuperior")
        .hasValueEqualTo("ou=People,dc=example,dc=com");
  }

  @SneakyThrows
  @Test
  void protocolOp_shouldProvideBindRequestFields() {
    var bindReq = new BindRequestImpl();
    bindReq.setMessageId(103);
    bindReq.setVersion3(true);
    bindReq.setName("cn=admin,dc=example,dc=com");
    bindReq.setSimple(true);

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, bindReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .extractChildWithPath("$.protocolOp.version")
        .hasValueEqualTo("3")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.name")
        .hasValueEqualTo("cn=admin,dc=example,dc=com")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.simple")
        .hasValueEqualTo("true");
  }

  @SneakyThrows
  @Test
  void protocolOp_shouldProvideCompareRequestFields() {
    var compareReq = new CompareRequestImpl();
    compareReq.setMessageId(104);
    compareReq.setName(new Dn("cn=test,dc=example,dc=com"));
    compareReq.setAttributeId("userPassword");
    compareReq.setAssertionValue("secret");

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, compareReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .extractChildWithPath("$.protocolOp.dn")
        .hasValueEqualTo("cn=test,dc=example,dc=com")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.attributeDesc")
        .hasValueEqualTo("userPassword")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.assertionValue")
        .hasValueEqualTo("secret");
  }

  @SneakyThrows
  @Test
  void protocolOp_shouldProvideResultResponseFields() {
    var bindResp = new BindResponseImpl(105);
    bindResp.getLdapResult().setResultCode(ResultCodeEnum.SUCCESS);
    bindResp.getLdapResult().setDiagnosticMessage("Bind successful");

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, bindResp);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .extractChildWithPath("$.protocolOp.resultCode")
        .hasValueEqualTo("SUCCESS")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.diagnosticMessage")
        .hasValueEqualTo("Bind successful");
  }

  @SneakyThrows
  @Test
  void protocolOp_shouldProvideMatchedDnOnError() {
    var searchResp = new SearchResultDoneImpl(106);
    searchResp.getLdapResult().setResultCode(ResultCodeEnum.NO_SUCH_OBJECT);
    searchResp.getLdapResult().setMatchedDn(new Dn("dc=example,dc=com"));
    searchResp.getLdapResult().setDiagnosticMessage("Entry not found");

    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, searchResp);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .extractChildWithPath("$.protocolOp.resultCode")
        .hasValueEqualTo("NO_SUCH_OBJECT")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.matchedDN")
        .hasValueEqualTo("dc=example,dc=com")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.diagnosticMessage")
        .hasValueEqualTo("Entry not found");
  }

  @Test
  void attributeFacet_shouldContainMetadataForSearchResultEntryAttributes() {
    final RbelElement convertedElement = rbelConverter.convertElement(SEARCH_RESPONSE, null);

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.attributes")
        .hasFacet(RbelLdapAttributesFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.objectClass[0]")
        .hasFacet(RbelLdapAttributeMetadataFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.dc")
        .hasFacet(RbelLdapAttributeMetadataFacet.class);
  }

  @SneakyThrows
  @Test
  void attributeFacet_shouldExposeMultiValuedAttributesAsArray() {
    byte[] addRequest =
        buildAddRequest(
            40,
            "cn=Multi,dc=example,dc=com",
            "objectClass",
            "top",
            "objectClass",
            "person",
            "cn",
            "Multi");

    RbelElement convertedElement = rbelConverter.convertElement(addRequest, null);

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.attributes.objectClass[0]")
        .hasStringContentEqualTo("top")
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.objectClass[1]")
        .hasStringContentEqualTo("person");
  }

  @SneakyThrows
  @Test
  void attributeFacet_shouldExposeAddRequestAttributeMetadata() {
    byte[] addRequest =
        buildAddRequest(41, "cn=Meta,dc=example,dc=com", "cn", "Meta", "sn", "Data");

    RbelElement convertedElement = rbelConverter.convertElement(addRequest, null);

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.attributes.cn[0]")
        .hasFacet(RbelLdapAttributeMetadataFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.sn[0]")
        .hasFacet(RbelLdapAttributeMetadataFacet.class);
  }

  private static final LdapApiService codec = LdapApiServiceFactory.getSingleton();

  private static byte[] buildAddRequest(int messageId, String dn, String... attrPairs)
      throws Exception {
    AddRequestImpl addRequest = new AddRequestImpl();
    addRequest.setMessageId(messageId);
    DefaultEntry entry = new DefaultEntry(dn);
    for (int i = 0; i < attrPairs.length; i += 2) {
      entry.add(new DefaultAttribute(attrPairs[i], attrPairs[i + 1]));
    }
    addRequest.setEntry(entry);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, addRequest);
    return buffer.getBytes().array();
  }

  private static byte[] buildModifyRequest(int messageId, String dn, String attr, String newValue)
      throws Exception {
    ModifyRequestImpl modifyRequest = new ModifyRequestImpl();
    modifyRequest.setMessageId(messageId);
    modifyRequest.setName(new Dn(dn));
    modifyRequest.addModification(
        new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, attr, newValue));
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, modifyRequest);
    return buffer.getBytes().array();
  }

  @Test
  void convertMessage_shouldConvertAddRequestWithAttributes() throws Exception {
    byte[] addRequest =
        buildAddRequest(3, "cn=John Doe,dc=example,dc=com", "cn", "John", "sn", "Doe");
    RbelElement convertedElement = rbelConverter.convertElement(addRequest, null);
    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.attributes")
        .hasFacet(RbelLdapAttributesFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.cn[0]")
        .hasStringContentEqualTo("John")
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.sn[0]")
        .hasStringContentEqualTo("Doe");
  }

  @Test
  void convertMessage_shouldConvertModifyRequestWithModifications() throws Exception {
    byte[] modifyRequest = buildModifyRequest(4, "cn=John Doe,dc=example,dc=com", "cn", "Jane");
    RbelElement convertedElement = rbelConverter.convertElement(modifyRequest, null);
    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.attributes")
        .satisfies(
            attrElem -> {
              boolean hasModFacet = attrElem.hasFacet(RbelLdapModificationFacet.class);
              org.assertj.core.api.Assertions.assertThat(hasModFacet).isTrue();
            });
  }

  @Test
  void shouldRenderModifyRequestWithModificationDetails() throws Exception {
    byte[] modifyRequest = buildModifyRequest(4, "cn=John Doe,dc=example,dc=com", "cn", "Jane");
    RbelElement convertedElement = rbelConverter.convertElement(modifyRequest, null);
    String html = RbelHtmlRenderer.render(List.of(convertedElement));
    assertThat(html)
        .contains("Modification Operation")
        .contains("Modification Attribute Name")
        .contains("Modification Value 1")
        .contains("Jane");
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertBindRequestAndResponse() {
    // BindRequest
    var bindReq = new BindRequestImpl();
    bindReq.setMessageId(10);
    bindReq.setVersion3(true);
    bindReq.setName("cn=admin,dc=example,dc=com");
    bindReq.setSimple(true);
    bindReq.setCredentials("secret".getBytes());
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, bindReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.BIND_REQUEST);
    // BindResponse
    var bindResp = new BindResponseImpl(11);
    Asn1Buffer bufferResp = new Asn1Buffer();
    LdapEncoder.encodeMessage(bufferResp, codec, bindResp);
    RbelElement respElem = rbelConverter.convertElement(bufferResp.getBytes().array(), null);
    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.BIND_RESPONSE);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertUnbindRequest() {
    var unbindReq = new UnbindRequestImpl();
    unbindReq.setMessageId(12);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, unbindReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(elem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.UNBIND_REQUEST);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertSaslBindRequest() {
    var bindReq = new BindRequestImpl();
    bindReq.setMessageId(50);
    bindReq.setVersion3(true);
    bindReq.setName("cn=admin,dc=example,dc=com");
    bindReq.setSimple(false);
    bindReq.setSaslMechanism("EXTERNAL");
    bindReq.setCredentials("sasl-creds".getBytes());
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, bindReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(elem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.BIND_REQUEST)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.simple")
        .hasValueEqualTo("false")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.saslMechanism")
        .hasValueEqualTo("EXTERNAL")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.saslCredentials")
        .hasValueEqualTo("sasl-creds".getBytes());
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertModifyDnRequestAndResponse() {
    var modDnReq = new ModifyDnRequestImpl();
    modDnReq.setMessageId(13);
    modDnReq.setName(new Dn("cn=John Doe,dc=example,dc=com"));
    modDnReq.setNewRdn(new Rdn("cn=Jane Doe"));
    modDnReq.setDeleteOldRdn(true);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, modDnReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.MODIFY_DN_REQUEST);
    // Response
    var modDnResp = new ModifyDnResponseImpl(14);
    Asn1Buffer bufferResp = new Asn1Buffer();
    LdapEncoder.encodeMessage(bufferResp, codec, modDnResp);
    RbelElement respElem = rbelConverter.convertElement(bufferResp.getBytes().array(), null);
    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.MODIFY_DN_RESPONSE);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertCompareRequestAndResponse() {
    var compareReq = new CompareRequestImpl();
    compareReq.setMessageId(15);
    compareReq.setName(new Dn("cn=John Doe,dc=example,dc=com"));
    compareReq.setAttributeId("cn");
    compareReq.setAssertionValue("Jane");
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, compareReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.COMPARE_REQUEST);
    // Response
    var compareResp = new CompareResponseImpl(16);
    Asn1Buffer bufferResp = new Asn1Buffer();
    LdapEncoder.encodeMessage(bufferResp, codec, compareResp);
    RbelElement respElem = rbelConverter.convertElement(bufferResp.getBytes().array(), null);
    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.COMPARE_RESPONSE);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertAbandonRequest() {
    var abandonReq = new AbandonRequestImpl();
    abandonReq.setMessageId(17);
    abandonReq.setAbandoned(5);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, abandonReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(elem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.ABANDON_REQUEST)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.abandonedMessageId")
        .hasValueEqualTo(5);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertModifyRequestWithMultipleModifications() {
    var modReq = new ModifyRequestImpl();
    modReq.setMessageId(21);
    modReq.setName(new Dn("cn=John Doe,dc=example,dc=com"));
    modReq.addModification(
        new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "cn", "Jane"));
    modReq.addModification(
        new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, "sn", "Doe"));
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, modReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(elem).hasFacet(RbelLdapFacet.class);
    // Check both modifications are present in the facets (they are attached to $.attributes)
    RbelElement attrs = elem.findElement("$.attributes").orElseThrow();
    long modCount =
        attrs.getFacets().stream().filter(RbelLdapModificationFacet.class::isInstance).count();
    assertThat(modCount).isGreaterThanOrEqualTo(2);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldHandleBinaryAttributeValues() {
    var addReq = new AddRequestImpl();
    addReq.setMessageId(22);
    addReq.setEntry(new DefaultEntry("cn=Binary,dc=example,dc=com"));
    addReq.getEntry().add(new DefaultAttribute("jpegPhoto", new byte[] {1, 2, 3, 4, 5}));
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, addReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(elem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.attributes.jpegPhoto[0]")
        .satisfies(e -> assertThat(e.getRawContent()).containsExactly(1, 2, 3, 4, 5));
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldHandleEmptyFields() {
    var addReq = new AddRequestImpl();
    addReq.setMessageId(23);
    addReq.setEntry(new DefaultEntry("cn=Empty,dc=example,dc=com"));
    addReq.getEntry().add(new DefaultAttribute("description", ""));
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, addReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(elem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.attributes.description[0]")
        .hasStringContentEqualTo("");
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldHandleComplexControl() {
    codec.registerRequestControl(new OpaqueControlFactory(codec, "1.2.3.4.5"));
    codec.registerResponseControl(new OpaqueControlFactory(codec, "1.2.3.4.5"));
    var delReq = new DeleteRequestImpl();
    delReq.setMessageId(24);
    delReq.setName(new Dn("cn=John Doe,dc=example,dc=com"));
    var control = new OpaqueControl("1.2.3.4.5");
    control.setCritical(true);
    control.setEncodedValue(new byte[] {9, 8, 7});
    delReq.addControl(control);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, delReq);
    RbelElement elem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(elem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.controls.oid_1_2_3_4_5")
        .satisfies(
            e -> {
              String content = e.printValue().orElse("");
              assertThat(content).contains("isCritical=true");
              assertThat(content).contains("0x09 0x08 0x07");
            });
  }

  @Test
  void convertMessage_shouldHandleMalformedMessage() {
    byte[] malformed = {0x30, 0x01, 0x02}; // Too short
    RbelElement elem = rbelConverter.convertElement(malformed, null);
    assertThat(elem.hasFacet(RbelLdapFacet.class)).isFalse();
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertStartTlsRequestAndResponse() {
    var startTlsReq = new StartTlsRequestImpl();
    startTlsReq.setMessageId(30);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, startTlsReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.START_TLS_REQUEST);
    var startTlsResp = new StartTlsResponseImpl(31);
    Asn1Buffer bufferResp = new Asn1Buffer();
    LdapEncoder.encodeMessage(bufferResp, codec, startTlsResp);
    RbelElement respElem = rbelConverter.convertElement(bufferResp.getBytes().array(), null);
    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.START_TLS_RESPONSE);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertPasswordModifyRequestAndResponse() throws Exception {
    var pwdModReq = new PasswordModifyRequestImpl();
    pwdModReq.setMessageId(32);
    pwdModReq.setUserIdentity("uid=testuser".getBytes());
    pwdModReq.setOldPassword("oldpass".getBytes());
    pwdModReq.setNewPassword("newpass".getBytes());
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, pwdModReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.PASSWORD_MODIFY_REQUEST)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.userIdentity")
        .hasValueEqualTo("uid=testuser".getBytes())
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.oldPassword")
        .hasValueEqualTo("oldpass".getBytes())
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.newPassword")
        .hasValueEqualTo("newpass".getBytes());

    var genPasswordSeq = new ASN1EncodableVector();
    genPasswordSeq.add(new DERTaggedObject(false, 0, new DEROctetString("generated".getBytes())));
    byte[] responseValue = new DERSequence(genPasswordSeq).getEncoded(ASN1Encoding.DER);

    byte[] encoded = encodeExtendedResponse(33, "1.3.6.1.4.1.4203.1.11.1", responseValue);

    RbelElement respElem = rbelConverter.convertElement(encoded, null);

    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.PASSWORD_MODIFY_RESPONSE)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.genPassword")
        .hasValueEqualTo("generated".getBytes());
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertWhoAmIRequestAndResponse() {
    var whoAmIReq = new WhoAmIRequestImpl();
    whoAmIReq.setMessageId(34);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, whoAmIReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.WHO_AM_I_REQUEST);

    byte[] authzId = "dn:uid=testuser,dc=example,dc=com".getBytes();
    byte[] encoded = encodeExtendedResponse(35, "1.3.6.1.4.1.4203.1.11.3", authzId);
    RbelElement respElem = rbelConverter.convertElement(encoded, null);
    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.WHO_AM_I_RESPONSE)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.authzId")
        .hasValueEqualTo(authzId);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertCancelRequestAndResponse() {
    codec.registerExtendedRequest(new CancelFactory(codec));
    var cancelReq = new CancelRequestImpl();
    cancelReq.setMessageId(36);
    cancelReq.setCancelId(1234);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, cancelReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);
    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.CANCEL_REQUEST)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.cancelId")
        .hasValueEqualTo(1234);

    byte[] encoded = encodeExtendedResponse(37, "1.3.6.1.1.8", null);
    RbelElement respElem = rbelConverter.convertElement(encoded, null);

    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.CANCEL_RESPONSE);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertGenericExtendedRequestAndResponse() {
    // Use WhoAmI extended request/response for testing generic extended operation handling
    var whoAmIReq = new WhoAmIRequestImpl();
    whoAmIReq.setMessageId(40);
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, whoAmIReq);
    RbelElement reqElem = rbelConverter.convertElement(buffer.getBytes().array(), null);

    assertThat(reqElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.WHO_AM_I_REQUEST)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.requestName")
        .matches(
            requestName -> requestName.printValue().filter(s -> s.contains("WhoAmI")).isPresent());

    // Test generic ExtendedResponse
    String testOid = "1.2.3.4.5.6.7";
    byte[] extendedResponseBytes = encodeExtendedResponse(41, testOid, null);
    RbelElement respElem = rbelConverter.convertElement(extendedResponseBytes, null);

    assertThat(respElem)
        .hasFacet(RbelLdapFacet.class)
        .extractChildWithPath("$.protocolOp.operationType")
        .hasValueEqualTo(LdapOperationType.EXTENDED_RESPONSE)
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp.responseName")
        .hasValueEqualTo(testOid);
  }

  private byte[] encodeExtendedResponse(int messageId, String responseName, byte[] responseValue)
      throws IOException {
    // Build LDAPResult components
    ASN1Enumerated resultCode = new ASN1Enumerated(0); // SUCCESS = 0
    DEROctetString matchedDN = new DEROctetString(new byte[0]); // empty
    DEROctetString diagnosticMessage = new DEROctetString(new byte[0]); // empty

    // Build Extended Response components
    var components = new ASN1EncodableVector();
    components.add(resultCode);
    components.add(matchedDN);
    components.add(diagnosticMessage);

    // Add optional responseName [10] CONTEXT SPECIFIC
    if (responseName != null) {
      components.add(
          new DERTaggedObject(
              false, 10, new DEROctetString(responseName.getBytes(StandardCharsets.US_ASCII))));
    }

    // Add optional responseValue [11] CONTEXT SPECIFIC
    if (responseValue != null) {
      components.add(new DERTaggedObject(false, 11, new DEROctetString(responseValue)));
    }

    // ExtendedResponse ::= [APPLICATION 24] IMPLICIT SEQUENCE { ... }
    // We DER-encode the SEQUENCE and then patch the tag byte from 0x30 (SEQUENCE) to
    // 0x78 ([APPLICATION 24], constructed). This yields the correct LDAP protocolOp encoding.
    DERSequence responseContent = new DERSequence(components);
    byte[] extRespDer = responseContent.getEncoded(ASN1Encoding.DER);
    extRespDer[0] = 0x78;
    var extendedResponse = ASN1Primitive.fromByteArray(extRespDer);

    // Build complete LDAP message
    var messageComponents = new ASN1EncodableVector();
    messageComponents.add(new ASN1Integer(messageId));
    messageComponents.add(extendedResponse);

    DERSequence ldapMessage = new DERSequence(messageComponents);
    return ldapMessage.getEncoded(ASN1Encoding.DER);
  }
}
