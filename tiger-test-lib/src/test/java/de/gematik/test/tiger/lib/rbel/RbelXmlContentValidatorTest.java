/*
 *
 * Copyright 2024 gematik GmbH
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
 */
package de.gematik.test.tiger.lib.rbel;

import static de.gematik.test.tiger.util.CurlTestdataUtil.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelXmlContentValidatorTest {

  private RbelXmlContentValidator rbelValidator;

  @BeforeEach
  void setUp() {
    this.rbelValidator = new RbelXmlContentValidator();
  }

  @Test
  void testSourceTestInvalid_NOK() {
    assertThatThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body><!-- test comment-- --></body>",
                    "<root><header></header><body></body></root>"))
        .isInstanceOf(org.xmlunit.XMLUnitException.class);
  }

  @Test
  void testSourceOracleInvalid_NOK() {
    assertThatThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body><!-- test comment --></body></root>",
                    "<root><header></header><body></body>"))
        .isInstanceOf(org.xmlunit.XMLUnitException.class);
  }

  @Test
  void testSourceNoComment_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body><!-- test comment --></body></root>",
                    "<root><header></header><body></body></root>",
                    "nocomment"));
  }

  @Test
  void testSourceNoCommetTxtTrim_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body>test     <!-- test comment --></body></root>",
                    "<root><header></header><body>test</body></root>",
                    "nocomment,txttrim"));
  }

  @Test
  void testSourceNoCommetTxtTrim2_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body>    test     <!-- test comment --></body></root>",
                    "<root><header></header><body>test</body></root>",
                    "nocomment,txttrim"));
  }

  @Test
  void testSourceNoCommetTxtTrim3_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body>    test xxx    <!-- test comment"
                        + " --></body></root>",
                    "<root><header></header><body>test xxx</body></root>",
                    "nocomment,txttrim"));
  }

  @Test
  void testSourceNoCommetTxtTrim4_OK() {
    assertThatThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body>    test   xxx    <!-- test comment"
                        + " --></body></root>",
                    "<root><header></header><body>test xxx</body></root>",
                    "nocomment,txttrim"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testSourceNoCommetTxtNormalize_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body>  test    xxxx   </body>  <!-- test comment"
                        + " --></root>",
                    "<root><header></header><body>test xxxx </body></root>",
                    "nocomment,txtnormalize"));
  }

  @Test
  void testSourceAttrOrder_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelValidator.compareXMLStructure(
                    "<root><header></header><body attr1='1'   attr2='2'></body></root>",
                    "<root><header></header><body attr2='2' attr1='1'></body></root>"));
  }

  @Test
  public void testCompareXMLStructureOfRbelElement() {
    String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/testdata/sampleCurlMessages/xmlMessage.curl");
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
    final RbelElement registryResponseNode =
        convertedMessage
            .findRbelPathMembers("$.body.RegistryResponse.RegistryErrorList.RegistryError[0]")
            .get(0);

    String oracle =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
            + "<ns2:RegistryError xmlns:ns2=\"urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0\""
            + " errorCode=\"XDSDuplicateUniqueIdInRegistry\""
            + " severity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Warning\">\n"
            + "            text in element\n"
            + "        </ns2:RegistryError>";
    String diffOptionCSV = "nocomment,txttrim";
    rbelValidator.verify(oracle, registryResponseNode, diffOptionCSV);
  }
}
