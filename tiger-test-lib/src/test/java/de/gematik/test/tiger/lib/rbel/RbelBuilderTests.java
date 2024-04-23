/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.builder.RbelBuilder;
import de.gematik.rbellogger.builder.RbelBuilderManager;
import de.gematik.rbellogger.builder.RbelObjectJexl;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelSerializationAssertion;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;

@Slf4j
class RbelBuilderTests {

  String jsonTest =
      """
        {
            "blub": {
                "foo":"bar"
            }
        }
        """;

  String jsonTestModified =
      """
        {
            "blub": {
                "foo": {
                    "some": "object"
                }
            }
        }
        """;

  String xmlTest = """
            <blub>
                foo
            </blub>
            """;

  static String complexJsonObject =
      """
        {
            "entry1": {
                "entry1a": {
                    "entry1aI": "stringValue1"
                }
            },
            "entry2": [
                {
                    "entry2a": {
                        "entry2aI": 0,
                        "entry2aII": {
                            "entry2aIIx": "stringValue2",
                            "entry2aIIy": "stringValue3"
                        },
                        "entry2aIII": [
                            "some",
                            "more",
                            "values",
                            "and",
                            "a",
                            "number",
                            5,
                            "and",
                            "an",
                            {
                                "object": {
                                    "key": "stringValue4"
                                }
                            }
                        ],
                        "entry2aIV": "stringValue5"
                    }
                },
                "stringValue6",
                "stringValue7"
            ],
            "entry3": {
                "entry3a": {
                    "entry3aI": "stringValue8"
                },
                "entry3b": {
                    "entry3bI": "=$/=)$§)(=)$/$=§",
                    "entry3bII": ":;DFNÖWRGÄ FERJÖÄRHG",
                    "(/)§=$§)=)§": 3
                },
                "entry3c": []
            }
        }
    """;

  static String insertIntoComplexStringAtPathentry1aI(Object newValue) {
    JSONObject modifiedComplexJsonObject = new JSONObject(complexJsonObject);
    modifiedComplexJsonObject
        .getJSONObject("entry1")
        .getJSONObject("entry1a")
        .put("entry1aI", newValue);
    return modifiedComplexJsonObject.toString();
  }

  static String insertIntoComplexStringAtPathentry2aIII5(Object newValue) {
    JSONObject modifiedComplexJsonObject = new JSONObject(complexJsonObject);
    modifiedComplexJsonObject
        .getJSONArray("entry2")
        .getJSONObject(0)
        .getJSONObject("entry2a")
        .getJSONArray("entry2aIII")
        .put(5, newValue);
    return modifiedComplexJsonObject.toString();
  }

  static String insertIntoComplexStringAtPathentry3bSpecialChars(Object newValue) {
    JSONObject modifiedComplexJsonObject = new JSONObject(complexJsonObject);
    modifiedComplexJsonObject
        .getJSONObject("entry3")
        .getJSONObject("entry3b")
        .put("(/)§=$§)=)§", newValue);
    return modifiedComplexJsonObject.toString();
  }

  @Test
  void readRbelFromScratchTest() {
    RbelBuilder builder = RbelBuilder.fromScratch(RbelContentType.JWT);
    Assertions.assertNotNull(builder);
  }

  @Test
  void readRbelFromStringWithNameTest() {
    RbelBuilder builder = RbelBuilder.fromString(jsonTest);
    Assertions.assertNotNull(builder);
    Assertions.assertTrue(builder.getTreeRootNode().findElement("$.blub").isPresent());
    Assertions.assertTrue(builder.getTreeRootNode().findElement("$.blub.foo").isPresent());
  }

  @Test
  void readRbelFromJsonTest() {
    String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
    RbelBuilder builder = RbelBuilder.fromFile(filePath);
    Assertions.assertTrue(builder.getTreeRootNode().getChildNodes().size() > 0);
    Assertions.assertEquals(
        "blub", builder.getTreeRootNode().getFirst("blub").orElseThrow().getKey().orElseThrow());
  }

  @Test
  void readRbelFromJsonWithNameTest() {
    String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
    RbelBuilder builder = RbelBuilder.fromFile("test", filePath);
    Assertions.assertNotNull(builder);
    Assertions.assertEquals(
        "blub",
        builder.getTreeRootNode().findElement("$.test.blub").orElseThrow().getKey().orElseThrow());
  }

  @Test
  void readRbelFromXmlTest() {
    String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.xml";
    RbelBuilder builder = RbelBuilder.fromFile(filePath);
    Assertions.assertNotNull(builder);
    Assertions.assertTrue(
        builder.getTreeRootNode().getChildNodes().stream()
            .map(r -> r.getKey().orElseThrow())
            .toList()
            .contains("blub"));
  }

  @Test
  void setObjectAtTest() {
    RbelBuilder builder = RbelBuilder.fromString(jsonTest);
    builder.setValueAt("$.blub.foo", "{ 'some': 'object' }");
    Assertions.assertEquals(
        "object",
        builder
            .getTreeRootNode()
            .findElement("$.blub.foo.some")
            .orElseThrow()
            .getRawStringContent());
    builder.setValueAt("$.blub", "{ 'object': 'replaced' }");
    Assertions.assertEquals(
        "replaced",
        builder.getTreeRootNode().findElement("$.blub.object").orElseThrow().getRawStringContent());
    Assertions.assertFalse(builder.getTreeRootNode().findElement("$.blub.foo.some").isPresent());
  }

  @Test
  void setValueAtTest() {
    RbelBuilder builder = RbelBuilder.fromString(jsonTest);
    builder.setValueAt("$.blub.foo", "some string");
    Assertions.assertEquals(
        "some string",
        builder.getTreeRootNode().findElement("$.blub.foo").orElseThrow().getRawStringContent());
  }

  @Test
  void serializeRbelObjectTest() {
    RbelBuilder builder = RbelBuilder.fromString(jsonTest);
    builder.setValueAt("$.blub.foo", "{ 'some': 'object' }");
    Assertions.assertEquals(
        StringUtils.deleteWhitespace(jsonTestModified),
        StringUtils.deleteWhitespace(builder.serialize()));
  }

  @Test
  void simpleJsonJexlTest() {
    RbelBuilderManager rBManager = new RbelBuilderManager();
    rBManager.put("test", RbelBuilder.fromString(jsonTest));
    RbelObjectJexl.initJexl(rBManager);
    RbelSerializationAssertion.assertEquals(
        jsonTest,
        TigerGlobalConfiguration.resolvePlaceholders("!{rbelObject:serialize('test')}"),
        RbelContentType.JSON);
  }

  @Test
  void simpleXmlJexlTest() {
    RbelBuilderManager rBManager = new RbelBuilderManager();
    rBManager.put("test", RbelBuilder.fromString(xmlTest));
    RbelObjectJexl.initJexl(rBManager);
    RbelSerializationAssertion.assertEquals(
        xmlTest,
        TigerGlobalConfiguration.resolvePlaceholders("!{rbelObject:serialize('test')}"),
        RbelContentType.XML);
  }

  @Test
  void addEntryTestSuccess() {
    RbelBuilder builder = RbelBuilder.fromString(jsonTest);
    String newArray =
        """
                        {
                            "new_array": [
                              "with",
                              "some",
                              "entries"
                            ]
                        }
                """;
    String expectedAfterAdding =
        """
                                {
            "blub": {
                "foo":
                    {
                        "new_array": [
                                  "with",
                                  "some",
                                  "entries",
                                  "and",
                                  "some",
                                  "more"
                                ]
                    }
            }
        }

        """;
    String actualSerialized =
        builder
            .setValueAt("$.blub.foo", newArray)
            .addEntryAt("$.blub.foo.new_array", "and")
            .addEntryAt("$.blub.foo.new_array", "some")
            .addEntryAt("$.blub.foo.new_array", "more")
            .serialize();
    Assertions.assertEquals(
        StringUtils.deleteWhitespace(expectedAfterAdding),
        StringUtils.deleteWhitespace(actualSerialized));
  }

  @Test
  void addEntryTestNotArrayFailure() {
    RbelBuilder builder = RbelBuilder.fromString(jsonTest);
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> builder.addEntryAt("$.blub.foo", "new_entry"));
    builder.serialize();
  }

  private static Stream<Arguments> provideJsonRbelObjectExamples() {

    List<String> successPathParameters =
        List.of(
            "$.entry1.entry1a.entry1aI",
            "$.entry2.0.entry2a.entry2aIII.5",
            "$.entry3.entry3b.(/)§=$§)=)§");

    List<String> successNewValueParameters =
        List.of(
            "some text",
            "4",
            ")(§=?/ß",
            "{ \"inner\": \"object\" }",
            "[3, \"other\", \"values\"]",
            "[]",
            "");

    List<String> failurePathParameters =
        List.of("$.not.an.existing.path", "$.entry2.entry2a.entry2aIII");

    List<String> failureNewValueParameters =
        List.of("\"\"\"\"", "[missingQuotationMarks, 3]", "{missing: quotationMarks}");

    List<Arguments> successParameters = new ArrayList<>();
    for (String successPath : successPathParameters) {
      for (Object successNewValue : successNewValueParameters) {
        String expectedValue =
            switch (successPath) {
              case "$.entry1.entry1a.entry1aI" -> insertIntoComplexStringAtPathentry1aI(
                  convertNonPrimitiveToJson(successNewValue.toString()));
              case "$.entry2.0.entry2a.entry2aIII.5" -> insertIntoComplexStringAtPathentry2aIII5(
                  convertNonPrimitiveToJson(successNewValue.toString()));
              case "$.entry3.entry3b.(/)§=$§)=)§" -> insertIntoComplexStringAtPathentry3bSpecialChars(
                  convertNonPrimitiveToJson(successNewValue.toString()));
              default -> throw new NotImplementedException(
                  "SuccessPath %s is not yet implemented for Parameterized tests."
                      .formatted(successPath));
            };
        successParameters.add(Arguments.of(successPath, successNewValue, true, expectedValue));
      }
    }
    List<Arguments> failureParameters = new ArrayList<>();
    for (String failurePath : failurePathParameters) {
      for (Object failureNewValue : failureNewValueParameters) {
        failureParameters.add(Arguments.of(failurePath, failureNewValue, false, null));
      }
    }
    Stream<Arguments> successParameterStream = successParameters.stream();
    Stream<Arguments> failureParameterStream = failureParameters.stream();

    return Stream.concat(successParameterStream, failureParameterStream);
  }

  @ParameterizedTest
  @MethodSource("provideJsonRbelObjectExamples")
  void setValueTests(
      String insertPath, String newStringValue, boolean expectSuccess, String expectedResult) {

    RbelBuilder parameterizedRbelBuilder = RbelBuilder.fromString(complexJsonObject);

    if (expectSuccess) {
      System.out.println(
          RbelConverter.builder()
              .build()
              .convertElement(
                  parameterizedRbelBuilder.getTreeRootNode().getRawStringContent(), null)
              .printTreeStructureWithoutColors());
      parameterizedRbelBuilder.setValueAt(insertPath, newStringValue);
      log.info("Inserting value {} into path: {}", newStringValue, insertPath);
      System.out.println(
          RbelConverter.builder()
              .build()
              .convertElement(
                  parameterizedRbelBuilder.getTreeRootNode().getRawStringContent(), null)
              .printTreeStructureWithoutColors());
      JSONAssert.assertEquals(
          expectedResult, parameterizedRbelBuilder.getTreeRootNode().getRawStringContent(), true);
    } else {
      Assertions.assertThrows(
          Exception.class, () -> parameterizedRbelBuilder.setValueAt(insertPath, newStringValue));
    }
  }

  static Object convertNonPrimitiveToJson(String value) {
    try {
      return new JSONObject(value);
    } catch (JSONException e) {
      try {
        return new JSONArray(value);
      } catch (JSONException ne) {
        return value;
      }
    }
  }
}
