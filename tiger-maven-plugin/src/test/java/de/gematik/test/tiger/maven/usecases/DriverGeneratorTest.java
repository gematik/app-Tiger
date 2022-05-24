/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.usecases;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Slf4j
class DriverGeneratorTest {

    private final Logger logger = new Logger() {
        @Override
        public void info(final CharSequence message) {
            log.info(message.toString());
        }

        @Override
        public void debug(final CharSequence message) {
            log.debug(message.toString());

        }
    };

    final JavaParser javaParser = new JavaParser();

    @Test
    @DisplayName("Driver file should be generated with all configured and necessary glues and feature files")
    @SneakyThrows
    void generateDriverForFiles_DriverFileShouldBeGeneratedWithAllConfiguratedAndNecessaryGluesAndFeatureFiles(
        @TempDir final Path outputFolder, @TempDir final Path templateFolder) {
        // Preparation
        final var customTemplatePath = templateFolder.resolve("customDriverTemplate.jtmpl");
        Files.copy(getClass().getResourceAsStream(
            "customDriverTemplate.jtmpl"), customTemplatePath);
        Files.delete(outputFolder); // Simulate not existing output dir
        final var underTest = new DriverGenerator(List.of("pck.of.glue1", "glue2.pck"),
            "fancy.pck.of.driver",
            outputFolder,
            "Mops${ctr}IT",
            customTemplatePath, logger);

        // Execution
        underTest.generateDriverForFeatureFiles(
            List.of("relativeRessourceFeatureFile.feature", "/absoluteRessourceFeatureFile"));

        // Assertion
        assertAll(
            () -> assertEquals(
                getNormalizedJavaFrom(
                    "package: fancy.pck.of.driver\n"
                        + "feature: relativeRessourceFeatureFile.feature\n"
                        + "counter: 1\n"
                        + "glues: \"de.gematik.test.tiger.glue\",\"pck.of.glue1\", \"glue2.pck\"\n"
                        + "classname: Mops1IT\n"),
                getNormalizedJavaFrom(
                    outputFolder.resolve(Paths.get("fancy", "pck", "of", "driver", "Mops1IT.java")))
            ),
            () -> assertEquals(
                getNormalizedJavaFrom(
                    "package: fancy.pck.of.driver\n"
                        + "feature: /absoluteRessourceFeatureFile\n"
                        + "counter: 2\n"
                        + "glues: \"de.gematik.test.tiger.glue\",\"pck.of.glue1\", \"glue2.pck\"\n"
                        + "classname: Mops2IT\n"),
                getNormalizedJavaFrom(
                    outputFolder.resolve(Paths.get("fancy", "pck", "of", "driver", "Mops2IT.java")))
            )
        );
    }

    @Test
    @DisplayName("Should use appropriate defaults for optional parameters")
    @SneakyThrows
    void generateDriverForFeatureFiles_ShouldUseAppropriateDefaultsForOptionalParameters(
        @TempDir final Path outputFolder) {
        // Preparation
        final var underTest = new DriverGenerator(emptyList(), null,
            outputFolder,
            "Mops${ctr}IT",
            null, logger);

        // Execution
        underTest.generateDriverForFeatureFiles(List.of("featureFile.feature"));

        // Assertion
        assertEquals(
            getNormalizedJavaFrom(";\n"
                + "\n"
                + "import io.cucumber.junit.CucumberOptions;\n"
                + "import de.gematik.test.tiger.TigerCucumberRunner;\n"
                + "import org.junit.runner.RunWith;\n"
                + "\n"
                + "@RunWith(TigerCucumberRunner.class)\n"
                + "@CucumberOptions(" + "features = {\"featureFile.feature\"},"
                + " plugin = {\n" + "    \"json:target/cucumber-parallel/1.json\", \"de.gematik.test.tiger.TigerCucumberListener\" },"
                + " glue = {\"de.gematik.test.tiger.glue\""
                + "})\n"
                + "public class Mops1IT {\n"
                + "\n"
                + "}\n"),
            getNormalizedJavaFrom(
                outputFolder.resolve(Paths.get("Mops1IT.java")))
        );
    }

    @NotNull
    private CompilationUnit getNormalizedJavaFrom(final Path filePath) throws IOException {
        return javaParser.parse(filePath)
            .getResult().get();
    }

    @NotNull
    private CompilationUnit getNormalizedJavaFrom(@Language("TEXT") final String sourceCode) {
        return javaParser.parse(sourceCode)
            .getResult().get();
    }
}
