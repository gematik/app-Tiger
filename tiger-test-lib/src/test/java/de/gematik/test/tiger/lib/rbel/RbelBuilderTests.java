package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.builder.RbelBuilder;
import de.gematik.rbellogger.builder.RbelBuilderManager;
import de.gematik.rbellogger.builder.RbelObjectJexl;
import de.gematik.rbellogger.data.RbelSerializationAssertion;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RbelBuilderTests {

    String jsonTest = """
        {
            "blub": {
                "foo":"bar"
            }
        }
        """;

    String jsonTestModified = """
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

    @Test
    void readRbelFromScratchTest()  {
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
    void readRbelFromJsonTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
        RbelBuilder builder = RbelBuilder.fromFile(filePath);
        Assertions.assertTrue(builder.getTreeRootNode().getChildNodes().size() > 0);
        Assertions.assertEquals("blub", builder.getTreeRootNode().getFirst("blub").orElseThrow().getKey().orElseThrow());
    }

    @Test
    void readRbelFromJsonWithNameTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
        RbelBuilder builder = RbelBuilder.fromFile("test", filePath);
        Assertions.assertNotNull(builder);
        Assertions.assertEquals("blub", builder.getTreeRootNode().findElement("$.test.blub").orElseThrow().getKey().orElseThrow());
    }

    @Test
    void readRbelFromXmlTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.xml";
        RbelBuilder builder = RbelBuilder.fromFile(filePath);
        Assertions.assertNotNull(builder);
        Assertions.assertTrue(builder.getTreeRootNode().getChildNodes().stream().map(r -> r.getKey().orElseThrow()).toList().contains("blub"));
    }

    @Test
    void setObjectAtTest() {
        RbelBuilder builder = RbelBuilder.fromString(jsonTest);
        builder.setValueAt("$.blub.foo", "{ 'some': 'object' }");
        Assertions.assertEquals("object", builder.getTreeRootNode().findElement("$.blub.foo.some").orElseThrow().getRawStringContent());
        builder.setValueAt("$.blub", "{ 'object': 'replaced' }");
        Assertions.assertEquals("replaced", builder.getTreeRootNode().findElement("$.blub.object").orElseThrow().getRawStringContent());
        Assertions.assertFalse(builder.getTreeRootNode().findElement("$.blub.foo.some").isPresent());
    }

    @Test
    void setValueAtTest() {
        RbelBuilder builder = RbelBuilder.fromString(jsonTest);
        builder.setValueAt("$.blub.foo", "some string");
        Assertions.assertEquals("some string", builder.getTreeRootNode().findElement("$.blub.foo").orElseThrow().getRawStringContent());
    }

    @Test
    void serializeRbelObjectTest() {
        RbelBuilder builder = RbelBuilder.fromString(jsonTest);
        builder.setValueAt("$.blub.foo", "{ 'some': 'object' }");
        Assertions.assertEquals(StringUtils.deleteWhitespace(jsonTestModified), StringUtils.deleteWhitespace(builder.serialize()));
    }

    @Test
    void simpleJsonJexlTest() {
        RbelBuilderManager rBManager = new RbelBuilderManager();
        rBManager.put("test", RbelBuilder.fromString(jsonTest));
        RbelObjectJexl.initJexl(rBManager);
        RbelSerializationAssertion.assertEquals(jsonTest, TigerGlobalConfiguration.resolvePlaceholders("!{rbelObject:serialize('test')}"), RbelContentType.JSON);
    }

    @Test
    void simpleXmlJexlTest() {
        RbelBuilderManager rBManager = new RbelBuilderManager();
        rBManager.put("test", RbelBuilder.fromString(xmlTest));
        RbelObjectJexl.initJexl(rBManager);
        RbelSerializationAssertion.assertEquals(xmlTest, TigerGlobalConfiguration.resolvePlaceholders("!{rbelObject:serialize('test')}"), RbelContentType.XML);
    }


    @Test
    void addEntryTestSuccess() {
        RbelBuilder builder = RbelBuilder.fromString(jsonTest);
        String newArray = """
                        {
                            "new_array": [
                              "with",
                              "some",
                              "entries"
                            ]
                        }
                """;
        String expectedAfterAdding = """
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
        String actualSerialized = builder.setValueAt("$.blub.foo", newArray)
                .addEntryAt("$.blub.foo.new_array", "and")
                .addEntryAt("$.blub.foo.new_array", "some")
                .addEntryAt("$.blub.foo.new_array", "more")
                .serialize();
        Assertions.assertEquals(StringUtils.deleteWhitespace(expectedAfterAdding), StringUtils.deleteWhitespace(actualSerialized));
    }

    @Test
    void addEntryTestNotArrayFailure() {
        RbelBuilder builder = RbelBuilder.fromString(jsonTest);
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.addEntryAt("$.blub.foo", "new_entry"));
        builder.serialize();
    }
}
