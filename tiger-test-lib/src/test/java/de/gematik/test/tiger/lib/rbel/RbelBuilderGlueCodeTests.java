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
package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.rbellogger.builder.RbelBuilder;
import de.gematik.rbellogger.data.RbelSerializationAssertion;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RbelBuilderGlueCode;
import de.gematik.test.tiger.glue.TigerParameterTypeDefinitions;
import de.gematik.test.tiger.lib.TigerDirector;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

class RbelBuilderGlueCodeTests {

  RbelBuilderGlueCode glueCode = new RbelBuilderGlueCode();
  String nestedObjectAsJsonString =
      """
            {
            	"blub": {
            		"foo":"bar"
            	}
            }
            """;
  String testFilePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
  String fileContentFromJexl = "!{file('%s')}".formatted(testFilePath);

  @BeforeAll
  static void startTigerDirector() {
    TigerGlobalConfiguration.reset();
    TigerDirector.readConfiguration();
  }

  @Test
  void createFromContentTestWithStringValue() {
    RbelBuilder expectedBuilder = RbelBuilder.fromString(nestedObjectAsJsonString);
    RbelSerializationAssertion.assertEquals(
        expectedBuilder.serialize(), nestedObjectAsJsonString, RbelContentType.JSON);

    Assertions.assertDoesNotThrow(
        () -> {
          glueCode.createFromContent("blub", nestedObjectAsJsonString);
          glueCode.assertJexlOutputEquals(
              resolve("!{rbelObject:serialize(\"blub\")}"),
              nestedObjectAsJsonString,
              RbelContentType.JSON);
        });
  }

  @Test
  void createFromContentTestWithJexlFromFile() {
    RbelBuilder expectedBuilder = RbelBuilder.fromFile(testFilePath);
    RbelSerializationAssertion.assertEquals(
        expectedBuilder.serialize(),
        TigerGlobalConfiguration.resolvePlaceholders(fileContentFromJexl),
        RbelContentType.JSON);
    Assertions.assertDoesNotThrow(
        () -> {
          glueCode.createFromContent("blub", resolve(fileContentFromJexl));
          glueCode.assertJexlOutputEquals(
              resolve("!{rbelObject:serialize(\"blub\")}"),
              resolve(fileContentFromJexl),
              RbelContentType.JSON);
        });
  }

  @Test
  void setValueAtTest() {
    String expectedBlub =
        """
            {
            	"blub": {
            		"foo":"bar"
            	},
            	"blib": {
            	    "new": "entry"
            	}
            }
                """;

    RbelBuilder expectedBuilder = RbelBuilder.fromString(nestedObjectAsJsonString);
    expectedBuilder.setValueAt("$.blib", "{ \"new\": \"entry\" }");
    Assertions.assertEquals(
        "entry",
        expectedBuilder
            .getTreeRootNode()
            .findElement("$.blib.new")
            .orElseThrow()
            .getRawStringContent());
    RbelSerializationAssertion.assertEquals(
        expectedBlub, expectedBuilder.serialize(), RbelContentType.JSON);

    glueCode.createFromContent("blub", nestedObjectAsJsonString);
    glueCode.setValueAt("blub", "$.blib", "{ \"new\": \"entry\" }");
    glueCode.assertValueAtEquals("blub", "$.blib.new", "entry");
    glueCode.assertJexlOutputEquals(
        resolve("!{rbelObject:serialize(\"blub\")}"), expectedBlub, RbelContentType.JSON);
  }

  @Test
  void fromScratchTest() {
    String expectedBuild =
        """
                {
                    "new": {
                        "object": {
                            "with": "some",
                            "new": "values"
                        }
                    }
                }
                """;

    RbelBuilder expectedBuilder = RbelBuilder.fromScratch(RbelContentType.JSON);
    expectedBuilder.setValueAt(
        "$.new", "{ \"object\": { \"with\": \"some\", \"new\": \"values\" } }");
    Assertions.assertEquals(
        "some",
        expectedBuilder
            .getTreeRootNode()
            .findElement("$.new.object.with")
            .orElseThrow()
            .getRawStringContent());
    RbelSerializationAssertion.assertEquals(
        expectedBuild, expectedBuilder.serialize(), RbelContentType.JSON);

    glueCode.createFromScratch("newObject", RbelContentType.JSON);
    glueCode.setValueAt(
        "newObject", "$.new", "{ \"object\": { \"with\": \"some\", \"new\": \"values\" } }");
    glueCode.assertValueAtEquals("newObject", "$.new.object.with", "some");
    glueCode.assertJexlOutputEquals(
        resolve("!{rbelObject:serialize(\"newObject\")}"), expectedBuild, RbelContentType.JSON);
  }

  @Test
  void addEntryTest() {
    String testArray =
        """
                {
                    "array1": [
                        "entry1",
                        {
                            "innerArray": [
                                "innerEntry1",
                                "innerEntry2",
                                "innerEntry3"
                            ]
                        },
                        {
                            "some": "object"
                        }
                    ],
                    "array2": [
                        {
                            "some": {
                                "more": "stuff"
                            }
                        }
                    ]
                }
                """;

    String expectedResult =
        """
                {
                    "array1": [
                        "entry1",
                        {
                            "innerArray": [
                                "innerEntry1",
                                "innerEntry2",
                                "innerEntry3",
                                {
                                    "innerEntry4": [
                                        "crazy",
                                        "stuff"
                                    ]
                                }
                            ]
                        },
                        {
                            "some": "object"
                        }
                    ],
                    "array2": [
                        {
                            "some": {
                                "more": "stuff"
                            }
                        }
                    ]
                }
                """;

    RbelBuilder expectedBuilder =
        RbelBuilder.fromString(testArray)
            .addEntryAt("$.array1.1.innerArray", "{ \"innerEntry4\": [\"crazy\", \"stuff\" ] }");
    Assertions.assertEquals(
        "stuff",
        expectedBuilder
            .getTreeRootNode()
            .findElement("$.array1.1.innerArray.3.innerEntry4.1")
            .orElseThrow()
            .getRawStringContent());
    RbelSerializationAssertion.assertEquals(
        expectedResult, expectedBuilder.serialize(), RbelContentType.JSON);

    glueCode.createFromContent("arrayTest", testArray);
    glueCode.addEntryAt(
        "arrayTest", "$.array1.1.innerArray", "{ \"innerEntry4\": [\"crazy\", \"stuff\" ] }");
    glueCode.assertValueAtEquals("arrayTest", "$.array1.1.innerArray.3.innerEntry4.1", "stuff");
    glueCode.assertJexlOutputEquals(
        resolve("!{rbelObject:serialize(\"arrayTest\")}"), expectedResult, RbelContentType.JSON);
  }

  @ParameterizedTest
  @EnumSource(RbelContentType.class)
  void rbelParameterTypeTest(RbelContentType type) {
    Assertions.assertEquals(type, TigerParameterTypeDefinitions.rbelContentType(type.toString()));
  }

  private static String resolve(String string) {
    return TigerParameterTypeDefinitions.tigerResolvedString(string);
  }

  @Test
  void rbelBuilderChangelogTest() {
    String valueToSet =
        """
                        {
                            "array": [
                                "blib",
                                "blab"
                            ]
                        }
                        """;

    Logger loggerInGlueCode = (Logger) LoggerFactory.getLogger(RbelBuilderGlueCode.class);

    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    loggerInGlueCode.addAppender(listAppender);

    glueCode.createFromContent("test", nestedObjectAsJsonString);
    glueCode.setValueAt("test", "$.blub", valueToSet);
    // isListTypeNode() does not work -> attributeMap does not contain
    glueCode.addEntryAt("test", "$.blub.array", "blub");

    List<ILoggingEvent> logsList = listAppender.list;

    LoggingEvent e0 = (LoggingEvent) logsList.get(0);
    assertThat(e0)
        .hasFieldOrPropertyWithValue("level", Level.INFO)
        .extracting(LoggingEvent::getMessage)
        .asString()
        .containsIgnoringWhitespaces(
            "Changed Rbel object 'test' at '$.blub' to '{\r\"array\":\r[\r \"blib\",\r\"blab\"]}'");

    LoggingEvent e3 = (LoggingEvent) logsList.get(3);
    assertThat(e3)
        .hasFieldOrPropertyWithValue("level", Level.INFO)
        .extracting(LoggingEvent::getMessage)
        .asString()
        .containsIgnoringWhitespaces(
            "NewObject:\u001B[0;93m└──\u001B[0m\u001B[1;31m\u001B[0m(\u001B[0;34m{\"blub\":{\"array\":[\"blib\",\"blab\",\"blub\"]}}\u001B[0m)");
  }
}
