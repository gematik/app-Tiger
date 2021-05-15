package de.gematik.test.tiger.lib.parser;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.lib.parser.model.Testcase;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestJavaTestParser {

    @Test
    @Afo("A_20315-01")
    void testParseTestSourcesOK() {
        final JavaTestParser parser = new JavaTestParser();

        parser.parseDirectory(Paths.get("src", "test").toFile());
        final Map<String, List<Testcase>> tcs = parser.getParsedTestcasesPerAfo();

        assertThat(tcs).containsOnlyKeys("A_20315-01");
        assertThat(tcs.get("A_20315-01")).hasSize(1);
        assertThat(tcs.get("A_20315-01").get(0).getMethod()).isEqualTo("testParseTestSourcesOK");
    }

}
