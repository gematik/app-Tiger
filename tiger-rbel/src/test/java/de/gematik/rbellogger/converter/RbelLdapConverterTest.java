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
import de.gematik.rbellogger.facets.ldap.RbelLdapAttributesFacet;
import de.gematik.rbellogger.facets.ldap.RbelLdapFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
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
    final String expectedLdapMessage =
        "LDAPMessage(msgID=5, protocolOp=DeleteRequestProtocolOp(dn='dc=example,dc=com'),"
            + " controls={Control(oid=1.2.840.113556.1.4.805, isCritical=true, value={null})})";

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .hasFacet(RbelRequestFacet.class)
        .doesNotHaveChildWithPath("$.attributes")
        .extractChildWithPath("$.textRepresentation")
        .hasStringContentEqualTo(expectedLdapMessage)
        .andTheInitialElement()
        .extractChildWithPath("$.msgId")
        .hasStringContentEqualTo("5")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp")
        .hasStringContentEqualTo("DeleteRequestProtocolOp");
  }

  @Test
  void convertMessage_shouldConvertCorrectResponse() {
    final RbelElement convertedElement = rbelConverter.convertElement(SEARCH_RESPONSE, null);
    final String expectedLdapMessage =
        "LDAPMessage(msgID=2, "
            + "protocolOp=SearchResultEntryProtocolOp(dn='dc=example,dc=com', "
            + "attrs={Attribute(name=objectClass, values={'top', 'domain'}),"
            + "Attribute(name=dc, values={'example'})}))";

    assertThat(convertedElement)
        .hasFacet(RbelLdapFacet.class)
        .hasFacet(RbelResponseFacet.class)
        .extractChildWithPath("$.textRepresentation")
        .hasStringContentEqualTo(expectedLdapMessage)
        .andTheInitialElement()
        .extractChildWithPath("$.msgId")
        .hasStringContentEqualTo("2")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp")
        .hasStringContentEqualTo("SearchResultEntryProtocolOp")
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
        .hasStringContentEqualTo(expectedLdapMessage)
        .andTheInitialElement()
        .extractChildWithPath("$.msgId")
        .hasStringContentEqualTo("1")
        .andTheInitialElement()
        .extractChildWithPath("$.protocolOp")
        .hasStringContentEqualTo("SearchResultEntryProtocolOp")
        .andTheInitialElement()
        .extractChildWithPath("$.attributes")
        .hasFacet(RbelLdapAttributesFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.attributes.telematikid")
        .hasStringContentEqualTo("5-SMC-B-Testkarte-883110000129221");
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
          .hasStringContentEqualTo(entry.getValue());
    }
  }

  @Test
  void shouldRenderCleanHtml() {
    final RbelElement convertedElement = rbelConverter.convertElement(SEARCH_RESPONSE, null);
    final String html = RbelHtmlRenderer.render(List.of(convertedElement));

    assertThat(html)
        .contains("LDAPMessage(msgID=2")
        .contains("dn=" + quoted("dc=example,dc=com"))
        .contains(
            "Attribute(name=objectClass, values={" + quoted("top") + ", " + quoted("domain") + "})")
        .contains("Attribute(name=dc, values={" + quoted("example") + "})");
  }

  private String quoted(final String s) {
    return "&#x27;" + s + "&#x27;";
  }
}
