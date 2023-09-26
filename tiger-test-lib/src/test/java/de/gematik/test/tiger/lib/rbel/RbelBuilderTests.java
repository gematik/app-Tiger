package de.gematik.test.tiger.lib.rbel;

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

    @Test
    void readRbelFromScratchTest()  {
        RbelBuilder builder = RbelBuilder.fromScratch();
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
    void readRbelFromJwtTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.jwt";
        RbelBuilder builder = RbelBuilder.fromFile(filePath);
        Assertions.assertNotNull(builder);
    }

    @Test
    void setObjectAtTest() {
        RbelBuilder builder = RbelBuilder.fromString(jsonTest);
        builder.setObjectAt("$.blub.foo", "{ 'some': 'object' }");
        Assertions.assertEquals("object", builder.getTreeRootNode().findElement("$.blub.foo.some").orElseThrow().getRawStringContent());
        builder.setObjectAt("$.blub", "{ 'object': 'replaced' }");
        Assertions.assertEquals("replaced", builder.getTreeRootNode().findElement("$.blub.object").orElseThrow().getRawStringContent());
        Assertions.assertFalse(builder.getTreeRootNode().findElement("$.blub.foo.some").isPresent());
    }

    @Test
    void setValueAtTest() {
        RbelBuilder builder = RbelBuilder.fromString(jsonTest);
        builder.setValueAt("$.blub.foo", "some string");
        Assertions.assertEquals("some string", builder.getTreeRootNode().findElement("$.blub.foo").orElseThrow().getRawStringContent());
    }
}
