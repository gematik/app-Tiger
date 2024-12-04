/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.maven.usecases;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import uk.org.webcompere.systemstubs.SystemStubs;

@Slf4j
class DriverGeneratorTest {

  private final Log logger = new SystemStreamLog();
  final JavaParser javaParser = new JavaParser();

  @Test
  @DisplayName(
      "Driver file should be generated with all configured and necessary glues and feature files")
  @SneakyThrows
  void
      generateDriverForFiles_DriverFileShouldBeGeneratedWithAllConfiguratedAndNecessaryGluesAndFeatureFiles(
          @TempDir final Path outputFolder, @TempDir final Path templateFolder) {
    // Preparation
    Path customTemplatePath =
        prepareTempFolders(outputFolder, templateFolder, "customDriverTemplate.jtmpl");
    var props =
        GenerateDriverProperties.builder()
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
        () ->
            assertEquals(
                getNormalizedJavaFrom(
                    """
                                package: fancy.pck.of.driver
                                feature: relativeRessourceFeatureFile.feature
                                counter: 1
                                gluesCsv: de.gematik.test.tiger.glue,pck.of.glue1,glue2.pck
                                classname: Mops001IT
                                tagsAnnotation:
                                """),
                getNormalizedJavaFrom(
                    outputFolder.resolve(
                        Paths.get("fancy", "pck", "of", "driver", "Mops001IT.java")))),
        () ->
            assertEquals(
                getNormalizedJavaFrom(
                    """
                                package: fancy.pck.of.driver
                                feature: /absoluteRessourceFeatureFile
                                counter: 2
                                gluesCsv: de.gematik.test.tiger.glue,pck.of.glue1,glue2.pck
                                classname: Mops002IT
                                """),
                getNormalizedJavaFrom(
                    outputFolder.resolve(
                        Paths.get("fancy", "pck", "of", "driver", "Mops002IT.java")))));
  }

  @SneakyThrows
  private Path prepareTempFolders(Path outputFolder, Path templateFolder, String templateName) {
    final var customTemplatePath = templateFolder.resolve(templateName);
    Files.copy(getClass().getResourceAsStream(templateName), customTemplatePath);
    Files.delete(outputFolder); // Simulate not existing output dir
    return customTemplatePath;
  }

  @Test
  @DisplayName("Should use appropriate defaults for optional parameters")
  @SneakyThrows
  void generateDriverForFeatureFiles_ShouldUseAppropriateDefaultsForOptionalParameters(
      @TempDir final Path outputFolder) {
    // Preparation
    var props =
        GenerateDriverProperties.builder()
            .glues(emptyList())
            .outputFolder(outputFolder)
            .driverClassName("Mops${ctr}IT")
            .build();

    final var underTest = new DriverGenerator(props, logger);

    underTest.generateDriverForFeatureFiles(List.of("featureFile.feature"));

    // Assertion
    assertEquals(
        getNormalizedJavaFrom(
            """
                            ;

                            import io.cucumber.junit.platform.engine.Constants;
                            import org.junit.platform.suite.api.ConfigurationParameter;
                            import org.junit.platform.suite.api.IncludeEngines;
                            import org.junit.platform.suite.api.Suite;
                            import org.junit.platform.suite.api.SelectFile;

                            @Suite
                            @IncludeEngines("cucumber")
                            @SelectFile("featureFile.feature")
                            @ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "de.gematik.test.tiger.glue")
                            @ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "not @Ignore")
                            @ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME,
                                                    value = "io.cucumber.core.plugin.TigerSerenityReporterPlugin,json:target/cucumber-parallel/1.json")
                            public class Mops001IT {

                            }
                            """),
        getNormalizedJavaFrom(outputFolder.resolve(Paths.get("Mops001IT.java"))));
  }

  @Test
  void tagsAnnotation_shouldStayEmptyIfUndefined(
      @TempDir final Path outputFolder, @TempDir final Path templateFolder) throws Exception {
    SystemStubs.restoreSystemProperties(
        () -> {
          System.clearProperty("cucumber.filter.tags"); // just in case some other test change it
          Path customTemplatePath =
              prepareTempFolders(
                  outputFolder, templateFolder, "customTemplateWithTagsAnnotation.jtmpl");
          var props =
              GenerateDriverProperties.builder()
                  .outputFolder(outputFolder)
                  .driverClassName("Mops${ctr}IT")
                  .templateFile(customTemplatePath)
                  .build();

          final var underTest = new DriverGenerator(props, logger);

          underTest.generateDriverForFeatureFiles(List.of("featureFile.feature"));

          assertEquals(
              getNormalizedJavaFrom(""),
              getNormalizedJavaFrom(outputFolder.resolve(Paths.get("Mops001IT.java"))));
        });
  }

  @Test
  void tagsAnnotation_shouldContainFullAnnotationWhenTagsDefined(
      @TempDir final Path outputFolder, @TempDir final Path templateFolder) throws Exception {
    SystemStubs.restoreSystemProperties(
        () -> {
          System.setProperty("cucumber.filter.tags", "@MyCustomUnitTestTag");
          Path customTemplatePath =
              prepareTempFolders(
                  outputFolder, templateFolder, "customTemplateWithTagsAnnotation.jtmpl");
          var props =
              GenerateDriverProperties.builder()
                  .outputFolder(outputFolder)
                  .driverClassName("Mops${ctr}IT")
                  .templateFile(customTemplatePath)
                  .build();

          final var underTest = new DriverGenerator(props, logger);

          underTest.generateDriverForFeatureFiles(List.of("featureFile.feature"));

          assertEquals(
              getNormalizedJavaFrom(
                  """
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "@MyCustomUnitTestTag")
"""),
              getNormalizedJavaFrom(outputFolder.resolve(Paths.get("Mops001IT.java"))));
        });
  }

  @Test
  void tags_shouldContainDefinedTags(
      @TempDir final Path outputFolder, @TempDir final Path templateFolder) throws Exception {
    SystemStubs.restoreSystemProperties(
        () -> {
          System.setProperty("cucumber.filter.tags", "@MyCustomUnitTestTag");
          Path customTemplatePath =
              prepareTempFolders(outputFolder, templateFolder, "customTemplateWithTags.jtmpl");
          var props =
              GenerateDriverProperties.builder()
                  .outputFolder(outputFolder)
                  .driverClassName("Mops${ctr}IT")
                  .templateFile(customTemplatePath)
                  .build();

          final var underTest = new DriverGenerator(props, logger);

          underTest.generateDriverForFeatureFiles(List.of("featureFile.feature"));

          assertEquals(
              getNormalizedJavaFrom(
                  """
            @MyCustomUnitTestTag
            """),
              getNormalizedJavaFrom(outputFolder.resolve(Paths.get("Mops001IT.java"))));
        });
  }

  @Test
  void tags_shouldThrowExceptionWhenNoTagsDefined(
      @TempDir final Path outputFolder, @TempDir final Path templateFolder) throws Exception {
    SystemStubs.restoreSystemProperties(
        () -> {
          System.clearProperty("cucumber.filter.tags"); // just in case some other test change it
          Path customTemplatePath =
              prepareTempFolders(outputFolder, templateFolder, "customTemplateWithTags.jtmpl");
          var props =
              GenerateDriverProperties.builder()
                  .outputFolder(outputFolder)
                  .driverClassName("Mops${ctr}IT")
                  .templateFile(customTemplatePath)
                  .build();

          final var underTest = new DriverGenerator(props, logger);

          var listOfFeatures = List.of("featureFile.feature");

          assertThatThrownBy(() -> underTest.generateDriverForFeatureFiles(listOfFeatures))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "Template contains ${tags} placeholders but no replacement was found for it. "
                      + "Consider using the placeholder ${tagsAnnotation} which allows for an empty tags configuration.");
        });
  }

  @NotNull
  private CompilationUnit getNormalizedJavaFrom(final Path filePath) throws IOException {
    return javaParser.parse(filePath).getResult().get();
  }

  @NotNull
  private CompilationUnit getNormalizedJavaFrom(@Language("TEXT") final String sourceCode) {
    return javaParser.parse(sourceCode).getResult().get();
  }
}
