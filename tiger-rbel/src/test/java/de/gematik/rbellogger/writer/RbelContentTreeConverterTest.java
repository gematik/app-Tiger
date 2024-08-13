/*
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

package de.gematik.rbellogger.writer;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xml.sax.SAXException;
import org.xmlunit.assertj.XmlAssert;

class RbelContentTreeConverterTest {

  private final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();

  @SneakyThrows
  @Test
  void simpleXml_ShouldGiveContentTree() {
    final byte[] xmlInput = Files.readAllBytes(Path.of("src/test/resources/randomXml.xml"));
    final RbelElement input = rbelConverter.convertElement(xmlInput, null);

    RbelWriter writer = new RbelWriter(rbelConverter);
    final String output = new String(writer.serialize(input, new TigerJexlContext()).getContent());
    XmlAssert.assertThat(output).and(new String(xmlInput)).ignoreWhitespace().areIdentical();
  }

  @SneakyThrows
  @Test
  void tgrIfAsAsstributeInXml_shouldOnlyRenderCorrectNode() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            <?xml version="1.0"?>
            <rootNode>
                <toBeRendered tgrIf="1 != 5">yes!</toBeRendered>
                <notToBeRendered tgrIf="10 == 5">NOOOO!</notToBeRendered>
            </rootNode>
            """,
            null);

    XmlAssert.assertThat(
            new String(
                new RbelWriter(rbelConverter)
                    .serialize(input, new TigerJexlContext())
                    .getContent()))
        .and(
            """
                <?xml version="1.0"?>
                <rootNode>
                    <toBeRendered>yes!</toBeRendered>
                </rootNode>
                """)
        .ignoreWhitespace()
        .areIdentical();
  }

  @SneakyThrows
  @Test
  void tgrIfAsTagInXml_shouldOnlyRenderCorrectNode() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            <?xml version="1.0"?>
            <rootNode>
                <toBeRendered><tgrIf>1 != 5</tgrIf>yes!</toBeRendered>
                <notToBeRendered><tgrIf>10 == 5</tgrIf>NOOOO!</notToBeRendered>
            </rootNode>
            """,
            null);

    XmlAssert.assertThat(
            new String(
                new RbelWriter(rbelConverter)
                    .serialize(input, new TigerJexlContext())
                    .getContent()))
        .and(
            """
                <?xml version="1.0"?>
                <rootNode>
                    <toBeRendered>yes!</toBeRendered>
                </rootNode>
                """)
        .ignoreWhitespace()
        .areIdentical();
  }

  @SneakyThrows
  @Test
  void tgrForTagInXml_shouldIterateThroughLoop() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            <?xml version="1.0"?>
            <rootNode>
                <repeatedTag tgrFor="number : 1..3">entry</repeatedTag>
            </rootNode>
            """,
            null);

    XmlAssert.assertThat(
            new String(
                new RbelWriter(rbelConverter)
                    .serialize(input, new TigerJexlContext())
                    .getContent()))
        .and(
            """
                <?xml version="1.0"?>
                <rootNode>
                    <repeatedTag>entry</repeatedTag>
                    <repeatedTag>entry</repeatedTag>
                    <repeatedTag>entry</repeatedTag>
                </rootNode>
                """)
        .ignoreWhitespace()
        .areIdentical();
  }

  @SneakyThrows
  @Test
  void tgrForTagWithContextInXml_shouldIterateThroughLoopAndPrintCounter() {
    final RbelElement input =
        rbelConverter.convertElement(
            "<?xml version=\"1.0\"?>\n"
                + "<rootNode>\n"
                + "    <repeatedTag tgrFor=\"number : 5..7\">entry number ${number}</repeatedTag>\n"
                + "</rootNode>",
            null);

    XmlAssert.assertThat(
            new String(
                new RbelWriter(rbelConverter)
                    .serialize(input, new TigerJexlContext())
                    .getContent()))
        .and(
            """
                <?xml version="1.0"?>
                <rootNode>
                    <repeatedTag>entry number 5</repeatedTag>
                    <repeatedTag>entry number 6</repeatedTag>
                    <repeatedTag>entry number 7</repeatedTag>
                </rootNode>
                """)
        .ignoreWhitespace()
        .areIdentical();
  }

  @SneakyThrows
  @Test
  void writeUrlWithParameters() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
              "tgrEncodeAs": "url",
              "basicPath": "http://bluzb/fdsa",
              "parameters": {
                "foo": "bar"
              }
            }
            """,
            null);

    final String result =
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent());
    assertThat(result).isEqualTo("http://bluzb/fdsa?foo=bar");
  }

  @SneakyThrows
  @Test
  void writeJson() {
    final RbelElement input = rbelConverter.convertElement("{\"foo\":\"bar\"}".getBytes(), null);

    final String result =
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent());
    assertThatJson(result).isEqualTo("{'foo':'bar'}");
  }

  @SneakyThrows
  @Test
  void writeJsonWithArrays() {
    final String inputJson = "{\"foo\":[\"bar1\",\"bar2\"]}";
    final RbelElement input = rbelConverter.convertElement(inputJson.getBytes(), null);

    final String result =
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent());
    assertThatJson(result).isEqualTo(inputJson);
  }

  @SneakyThrows
  @Test
  void mixJsonInXml() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            <?xml version="1.0"?>
            <rootNode>
                <toBeRendered tgrIf="1 != 5">{"foo": "bar"}</toBeRendered>
            </rootNode>
            """,
            null);

    XmlAssert.assertThat(
            new String(
                new RbelWriter(rbelConverter)
                    .serialize(input, new TigerJexlContext())
                    .getContent()))
        .and(
            """
                <?xml version="1.0"?>
                <rootNode>
                    <toBeRendered>{"foo": "bar"}</toBeRendered>
                </rootNode>
                """)
        .ignoreWhitespace()
        .areIdentical();
  }

  @SneakyThrows
  @Test
  void forLoopWithComplexIterable() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            <?xml version="1.0"?>
            <body>
                <blub>
                    <blub>
                        <tgrFor>person : persons</tgrFor>
                        <name>${person.name}</name>
                        <age>${person.age}</age>
                    </blub>
                    <blab tgrFor="number : 1..3">
                        <someInteger>${number}</someInteger>
                    </blab>
                </blub>
                <schmub tgrIf="1 &lt; 5" logic="still applies" />
            </body>
            """,
            null);

    TigerGlobalConfiguration.putValue("persons.0.name", "klaus");
    TigerGlobalConfiguration.putValue("persons.0.age", "52");
    TigerGlobalConfiguration.putValue("persons.1.name", "dieter");
    TigerGlobalConfiguration.putValue("persons.1.age", "42");

    final String output =
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent());

    XmlAssert.assertThat(output)
        .and(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <body>\s
                  <blub>\s
                    <blub>\s
                      <name>klaus</name> \s
                      <age>52</age>\s
                    </blub>
                    <blub>\s
                      <name>dieter</name> \s
                      <age>42</age>\s
                    </blub> \s
                    <blab>\s
                      <someInteger>1</someInteger>\s
                    </blab>
                    <blab>\s
                      <someInteger>2</someInteger>\s
                    </blab>
                    <blab>\s
                      <someInteger>3</someInteger>\s
                    </blab>\s
                  </blub> \s
                  <schmub logic="still applies"></schmub>\s
                </body>
                """)
        .ignoreWhitespace()
        .areSimilar();
  }

  @SneakyThrows
  @Test
  void modeSwitchJsonInXml() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            <?xml version="1.0"?>
            <rootNode>
                <toBeRendered tgrFor="number : 1..3"><foo tgrEncodeAs="json">${number}</foo></toBeRendered>
            </rootNode>
            """,
            null);

    final String output =
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent());
    XmlAssert.assertThat(output)
        .and(
            """
                <?xml version="1.0" encoding="UTF-8"?>

                <rootNode>\s
                  <toBeRendered>{"foo": "1"}</toBeRendered>
                  <toBeRendered>{"foo": "2"}</toBeRendered>
                  <toBeRendered>{"foo": "3"}</toBeRendered>\s
                </rootNode>
                """)
        .ignoreWhitespace()
        .areIdentical();
  }

  @SneakyThrows
  @Test
  void tgrIfTagInJson_shouldOnlyRenderCorrectNode() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
              "toBeRendered" : {
                "tgrIf": "1 != 5",
                "this": "should appear"
              },
              "notToBeRendered" : {
                "tgrIf": "1 == 5",
                "this": "should NOT appear"
              }
            }
            """,
            null);

    JSONAssert.assertEquals(
        """
                {
                  "toBeRendered" : {
                    "this": "should appear"
                  }
                }
                """,
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent()),
        false);
  }

  @SneakyThrows
  @Test
  void referenceContextElementInTgrIfExpression_shouldBeFound() {
    final RbelElement contextElement =
        rbelConverter.convertElement(
            readCurlFromFileWithCorrectedLineBreaks(
                "src/test/resources/sampleMessages/getRequest.curl"),
            null);

    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
              "toBeRendered" : {
                "tgrIf": "$.header.Connection == 'Keep-Alive'",
                "this": "should appear"
              }
            }
            """,
            null);

    final String serializedElement =
        new String(
            new RbelWriter(rbelConverter)
                .serialize(input, new TigerJexlContext().withRootElement(contextElement))
                .getContent());
    JSONAssert.assertEquals(
        """
            {
              "toBeRendered" : {
                "this": "should appear"
              }
            }
            """,
        serializedElement,
        false);
  }

  @SneakyThrows
  @Test
  void explicitlySetNodeToPrimitive_shouldBeHonored() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
              "primitiveValue" : {
                "tgrAttributes": ["jsonPrimitive"],
                "value": 12345
              }
            }
            """,
            null);

    final String serializedElement =
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent());
    JSONAssert.assertEquals(
        """
            {
              "primitiveValue" : 12345
            }""",
        serializedElement,
        false);
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({"\"12345\", 12345", "\"true\", true", "\"12.345\", 12.345", "\"-54321\", -54321"})
  void explicitlySetNodeToNonStringPrimitive_shouldBeHonored(String placeholder, String result) {
    final RbelElement input =
        rbelConverter.convertElement(
            "{\n"
                + "  \"primitiveValue\" : {\n"
                + "    \"tgrAttributes\": [\"jsonNonStringPrimitive\"],\n"
                + "    \"value\": "
                + placeholder
                + "\n"
                + "  }\n"
                + "}\n",
            null);

    final String serializedElement =
        new String(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent());
    JSONAssert.assertEquals("{\"primitiveValue\": " + result + "}", serializedElement, false);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"hello\": []}", "[1,2,3]", "['1','2','3']"})
  void testSerializationOfJson(String value) {
    RbelElement convertedRbelElement = rbelConverter.convertElement(value, null);

    String serializedJson =
        new RbelWriter(rbelConverter)
            .serialize(convertedRbelElement, new TigerJexlContext())
            .getContentAsString();

    JSONAssert.assertEquals(value, serializedJson, JSONCompareMode.LENIENT);
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
        <name><text value="Susanne Engelchen"/></name>,/name/text/@value
        <name text="Susanne Engelchen"/>,/name/@text
        """)
  void testSerializationOfXmlWithElementsNamedText(String xmlValue, String targetXPath)
      throws XpathException, IOException, SAXException {
    RbelElement convertedRbelElement = rbelConverter.convertElement(xmlValue, null);
    String serializedXml =
        new RbelWriter(rbelConverter)
            .serialize(convertedRbelElement, new TigerJexlContext())
            .getContentAsString();

    XMLAssert.assertXpathEvaluatesTo("Susanne Engelchen", targetXPath, serializedXml);
  }

  @SneakyThrows
  @Test
  void xmlWithNamespaces_shouldBeUnchangedAfterSerialization() {
    final byte[] xmlInput = Files.readAllBytes(Path.of("src/test/resources/xmlWithNamespaces.xml"));
    final RbelElement input = rbelConverter.convertElement(xmlInput, null);

    RbelWriter writer = new RbelWriter(rbelConverter);
    final String output = new String(writer.serialize(input, new TigerJexlContext()).getContent());
    XmlAssert.assertThat(output).and(new String(xmlInput)).ignoreWhitespace().areIdentical();
  }
}
