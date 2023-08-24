package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
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
        RbelBuilder builder = RbelBuilder.fromString("test", jsonTest);
        Assertions.assertNotNull(builder);
//        Assertions.assertEquals("test", builder.getTreeRootNode().childNodes().get(0).getKey()); // sollte mit TGR-1001 funktionieren
        Assertions.assertEquals("blub", builder.getTreeRootNode().childNodes().get(0).childNodes().get(0).getKey());
    }

    @Test
    void readRbelFromJsonTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
        RbelBuilder builder = RbelBuilder.fromFile(filePath);
        Assertions.assertTrue(builder.getTreeRootNode().childNodes().size() > 0);
        Assertions.assertEquals("blub", builder.getTreeRootNode().childNodes().get(0).getKey());
    }

    @Test
    void readRbelFromJsonWithNameTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.json";
        RbelBuilder builder = RbelBuilder.fromFile("test", filePath);
        Assertions.assertNotNull(builder);
        Assertions.assertEquals("blub", builder.getTreeRootNode().childNodes().get(0).childNodes().get(0).getKey());
    }

    @Test
    void readRbelFromXmlTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.xml";
        RbelBuilder builder = RbelBuilder.fromFile(filePath);
        Assertions.assertNotNull(builder);
        Assertions.assertTrue(builder.getTreeRootNode().childNodes().stream().map(RbelContentTreeNode::getKey).toList().contains("blub"));
    }

    @Test
    void readRbelFromJwtTest()  {
        String filePath = "src/test/resources/testdata/rbelBuilderTests/blub.jwt";
        RbelBuilder builder = RbelBuilder.fromFile(filePath);
        Assertions.assertNotNull(builder);
    }
}
