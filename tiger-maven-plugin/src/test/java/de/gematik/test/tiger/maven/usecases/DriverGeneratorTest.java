/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.usecases;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import de.gematik.test.tiger.maven.adapter.mojos.GenerateDriverProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Slf4j
class DriverGeneratorTest {

    private final Log logger = new SystemStreamLog();
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
        var props = GenerateDriverProperties.builder()
            .glues(List.of("pck.of.glue1", "glue2.pck"))
            .driverPackage("fancy.pck.of.driver")
            .outputFolder(outputFolder)
            .driverClassName("Mops${ctr}IT")
            .templateFile(customTemplatePath)
            .build();
        final var underTest = new DriverGenerator(props, logger);

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
                        + "classname: Mops001IT\n"),
                getNormalizedJavaFrom(
                    outputFolder.resolve(Paths.get("fancy", "pck", "of", "driver", "Mops001IT.java")))
            ),
            () -> assertEquals(
                getNormalizedJavaFrom(
                    "package: fancy.pck.of.driver\n"
                        + "feature: /absoluteRessourceFeatureFile\n"
                        + "counter: 2\n"
                        + "glues: \"de.gematik.test.tiger.glue\",\"pck.of.glue1\", \"glue2.pck\"\n"
                        + "classname: Mops002IT\n"),
                getNormalizedJavaFrom(
                    outputFolder.resolve(Paths.get("fancy", "pck", "of", "driver", "Mops002IT.java")))
            )
        );
    }

    @Test
    @DisplayName("Should use appropriate defaults for optional parameters")
    @SneakyThrows
    void generateDriverForFeatureFiles_ShouldUseAppropriateDefaultsForOptionalParameters(
        @TempDir final Path outputFolder) {
        // Preparation
        var props = GenerateDriverProperties.builder()
            .glues(emptyList())
            .outputFolder(outputFolder)
            .driverClassName("Mops${ctr}IT")
            .build();

        final var underTest = new DriverGenerator(props, logger);

        // Execution
        underTest.generateDriverForFeatureFiles(List.of("featureFile.feature"));

        // Assertion
        assertEquals(
            getNormalizedJavaFrom(";\n"
                + "\n"
                + "import io.cucumber.junit.CucumberOptions;\n"
                + "import net.serenitybdd.cucumber.CucumberWithSerenity;\n"
                + "import org.junit.runner.RunWith;\n"
                + "\n"
                + "@RunWith(CucumberWithSerenity.class)\n"
                + "@CucumberOptions(" + "features = {\"featureFile.feature\"},"
                + " plugin = {\n" + "    \"json:target/cucumber-parallel/1.json\", \"de.gematik.test.tiger.TigerCucumberListener\" },"
                + " glue = {\"de.gematik.test.tiger.glue\"},"
                + " tags = \"not @Ignore\""
                + ")\n"
                + "public class Mops001IT {\n"
                + "\n"
                + "}\n"),
            getNormalizedJavaFrom(
                outputFolder.resolve(Paths.get("Mops001IT.java")))
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
