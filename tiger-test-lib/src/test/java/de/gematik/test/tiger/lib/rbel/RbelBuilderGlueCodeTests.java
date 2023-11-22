package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.builder.RbelBuilder;
import de.gematik.rbellogger.data.RbelSerializationAssertion;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RbelBuilderGlueCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RbelBuilderGlueCodeTests {

  RbelBuilderGlueCode glueCode = new RbelBuilderGlueCode();
  String blub =
      """
            {
            	"blub": {
            		"foo":"bar"
            	}
            }
            """;
  String testFilePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
  String fileContentFromJexl = "!{file('%s')}".formatted(testFilePath);

  @Test
  void createFromContentTestWithStringValue() {
    RbelBuilder expectedBuilder = RbelBuilder.fromString(blub);
    RbelSerializationAssertion.assertEquals(
        expectedBuilder.serialize(), blub, RbelContentType.JSON);

    Assertions.assertDoesNotThrow(
        () -> {
          glueCode.createFromContent("blub", blub);
          glueCode.assertJexlOutputEquals(
              "!{rbelObject:serialize(\"blub\")}", blub, RbelContentType.JSON);
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
          glueCode.createFromContent("blub", fileContentFromJexl);
          glueCode.assertJexlOutputEquals(
              "!{rbelObject:serialize(\"blub\")}", fileContentFromJexl, RbelContentType.JSON);
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

    RbelBuilder expectedBuilder = RbelBuilder.fromString(blub);
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

    glueCode.createFromContent("blub", blub);
    glueCode.setValueAt("blub", "$.blib", "{ \"new\": \"entry\" }");
    glueCode.assertValueAtEquals("blub", "$.blib.new", "entry");
    glueCode.assertJexlOutputEquals(
        "!{rbelObject:serialize(\"blub\")}", expectedBlub, RbelContentType.JSON);
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
        "!{rbelObject:serialize(\"newObject\")}", expectedBuild, RbelContentType.JSON);
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
        "!{rbelObject:serialize(\"arrayTest\")}", expectedResult, RbelContentType.JSON);
  }

  @ParameterizedTest
  @EnumSource(RbelContentType.class)
  void rbelParameterTypeTest(RbelContentType type) {
    Assertions.assertEquals(type, glueCode.rbelContentType(type.toString()));
  }
}
