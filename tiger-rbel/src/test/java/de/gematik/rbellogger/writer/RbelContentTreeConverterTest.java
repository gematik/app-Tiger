/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

class RbelContentTreeConverterTest {

    private RbelConverter rbelConverter = RbelConverter.builder().build();

    @SneakyThrows
    @Test
    void simpleXml_ShouldGiveContentTree() {
        final byte[] xmlInput = Files.readAllBytes(Path.of("src/test/resources/randomXml.xml"));
        final RbelElement input = rbelConverter.convertElement(xmlInput, null);

        RbelWriter writer = new RbelWriter(rbelConverter);
        final String output = new String(writer.serialize(input));
        System.out.println(output);
        XmlAssert.assertThat(output)
            .and(new String(xmlInput))
            .ignoreWhitespace()
            .areIdentical();
    }

    @SneakyThrows
    @Test
    void tgrIfTagInXml_shouldOnlyRenderCorrectNode() {
        final RbelElement input = rbelConverter.convertElement("<?xml version=\"1.0\"?>\n"
            + "<rootNode>\n"
            + "    <toBeRendered tgrIf=\"1 != 5\">yes!"
            + "</toBeRendered>\n"
            + "    <notToBeRendered tgrIf=\"10 == 5\">NOOOO!</notToBeRendered>\n"
            + "</rootNode>", null);

        XmlAssert.assertThat(new String(new RbelWriter(rbelConverter).serialize(input)))
            .and("<?xml version=\"1.0\"?>\n"
                + "<rootNode>\n"
                + "    <toBeRendered>yes!</toBeRendered>\n"
                + "</rootNode>")
            .ignoreWhitespace()
            .areIdentical();
    }

    @SneakyThrows
    @Test
    void tgrForTagInXml_shouldIterateThroughLoop() {
        final RbelElement input = rbelConverter.convertElement("<?xml version=\"1.0\"?>\n"
            + "<rootNode>\n"
            + "    <repeatedTag tgrFor=\"number : 1..3\">entry</repeatedTag>\n"
            + "</rootNode>", null);

        XmlAssert.assertThat(new String(new RbelWriter(rbelConverter).serialize(input)))
            .and("<?xml version=\"1.0\"?>\n"
                + "<rootNode>\n"
                + "    <repeatedTag>entry</repeatedTag>\n"
                + "    <repeatedTag>entry</repeatedTag>\n"
                + "    <repeatedTag>entry</repeatedTag>\n"
                + "</rootNode>")
            .ignoreWhitespace()
            .areIdentical();
    }

    @SneakyThrows
    @Test
    void tgrForTagWithContextInXml_shouldIterateThroughLoopAndPrintCounter() {
        final RbelElement input = rbelConverter.convertElement("<?xml version=\"1.0\"?>\n"
            + "<rootNode>\n"
            + "    <repeatedTag tgrFor=\"number : 5..7\">entry number ${number}</repeatedTag>\n"
            + "</rootNode>", null);

        XmlAssert.assertThat(new String(new RbelWriter(rbelConverter).serialize(input)))
            .and("<?xml version=\"1.0\"?>\n"
                + "<rootNode>\n"
                + "    <repeatedTag>entry number 5</repeatedTag>\n"
                + "    <repeatedTag>entry number 6</repeatedTag>\n"
                + "    <repeatedTag>entry number 7</repeatedTag>\n"
                + "</rootNode>")
            .ignoreWhitespace()
            .areIdentical();
    }

    @SneakyThrows
    @Test
    void writeUrlWithParameters() {
        final RbelElement input = rbelConverter.convertElement("{\n"
            + "  \"tgrEncodeAs\": \"url\",\n"
            + "  \"basicPath\": \"http://bluzb/fdsa\",\n"
            + "  \"parameters\": {\n"
            + "    \"foo\": \"bar\"\n"
            + "  }\n"
            + "}", null);

        final String result = new String(new RbelWriter(rbelConverter).serialize(input));
        assertThat(result).isEqualTo("http://bluzb/fdsa?foo=bar");
    }

    @SneakyThrows
    @Test
    void writeJson() {
        final RbelElement input = rbelConverter.convertElement("{'foo':'bar'}".getBytes(), null);

        final String result = new String(new RbelWriter(rbelConverter).serialize(input));
        System.out.println(result);
        assertThatJson(result).isEqualTo("{'foo':'bar'}");
    }

    @SneakyThrows
    @Test
    void writeJsonWithArrays() {
        final String inputJson = "{\"foo\":[\"bar1\",\"bar2\"]}";
        final RbelElement input = rbelConverter.convertElement(inputJson.getBytes(), null);

        final String result = new String(new RbelWriter(rbelConverter).serialize(input));
        System.out.println(result);
        assertThatJson(result).isEqualTo(inputJson);
    }

    @SneakyThrows
    @Test
    void mixJsonInXml() {
        final RbelElement input = rbelConverter.convertElement("<?xml version=\"1.0\"?>\n"
            + "<rootNode>\n"
            + "    <toBeRendered tgrIf=\"1 != 5\">{\"foo\": \"bar\"}</toBeRendered>\n"
            + "</rootNode>", null);

        XmlAssert.assertThat(new String(new RbelWriter(rbelConverter).serialize(input)))
            .and("<?xml version=\"1.0\"?>\n"
                + "<rootNode>\n"
                + "    <toBeRendered>{\"foo\": \"bar\"}</toBeRendered>\n"
                + "</rootNode>")
            .ignoreWhitespace()
            .areIdentical();
    }

    @SneakyThrows
    @Test
    void forLoopWithComplexIterable() {
        final RbelElement input = rbelConverter.convertElement(
            "<?xml version=\"1.0\"?>\n"
                + "<body>\n"
                + "    <blub>\n"
                + "        <blub>\n"
                + "            <tgrFor>person : persons</tgrFor>\n"
                + "            <name>${person.name}</name>\n"
                + "            <age>${person.age}</age>\n"
                + "        </blub>\n"
                + "        <blab tgrFor=\"number : 1..3\">\n"
                + "            <someInteger>${number}</someInteger>\n"
                + "        </blab>\n"
                + "    </blub>\n"
                + "    <schmub tgrIf=\"1 &lt; 5\" logic=\"still applies\" />\n"
                + "</body>", null);

        TigerGlobalConfiguration.putValue("persons.0.name", "klaus");
        TigerGlobalConfiguration.putValue("persons.0.age", "52");
        TigerGlobalConfiguration.putValue("persons.1.name", "dieter");
        TigerGlobalConfiguration.putValue("persons.1.age", "42");

        final String output = new String(new RbelWriter(rbelConverter).serialize(input));
        System.out.println("\n\n\n" + output);

        XmlAssert.assertThat(output)
            .and("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<body> \n"
                + "  <blub> \n"
                + "    <blub> \n"
                + "      <name>klaus</name>  \n"
                + "      <age>52</age> \n"
                + "    </blub>\n"
                + "    <blub> \n"
                + "      <name>dieter</name>  \n"
                + "      <age>42</age> \n"
                + "    </blub>  \n"
                + "    <blab> \n"
                + "      <someInteger>1</someInteger> \n"
                + "    </blab>\n"
                + "    <blab> \n"
                + "      <someInteger>2</someInteger> \n"
                + "    </blab>\n"
                + "    <blab> \n"
                + "      <someInteger>3</someInteger> \n"
                + "    </blab> \n"
                + "  </blub>  \n"
                + "  <schmub logic=\"still applies\"></schmub> \n"
                + "</body>")
            .ignoreWhitespace()
            .areSimilar();
    }

    @SneakyThrows
    @Test
    void modeSwitchJsonInXml() {
        final RbelElement input = rbelConverter.convertElement("<?xml version=\"1.0\"?>\n"
            + "<rootNode>\n"
            + "    <toBeRendered tgrFor=\"number : 1..3\"><foo tgrEncodeAs=\"json\">${number}</foo></toBeRendered>\n"
            + "</rootNode>", null);

        final String output = new String(new RbelWriter(rbelConverter).serialize(input));
        System.out.println(output);
        XmlAssert.assertThat(output)
            .and("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "\n"
                + "<rootNode> \n"
                + "  <toBeRendered>{\"foo\": \"1\"}</toBeRendered>\n"
                + "  <toBeRendered>{\"foo\": \"2\"}</toBeRendered>\n"
                + "  <toBeRendered>{\"foo\": \"3\"}</toBeRendered> \n"
                + "</rootNode>\n")
            .ignoreWhitespace()
            .areIdentical();
    }
}
