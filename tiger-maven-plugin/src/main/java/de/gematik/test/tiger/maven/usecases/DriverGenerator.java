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

import de.gematik.test.tiger.maven.adapter.mojos.GenerateDriverProperties;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;

public class DriverGenerator {

  public static final String COUNTER_REPLACEMENT_TOKEN = "${ctr}";

  private static final String TAGS_ANNOTATION =
      "@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = \"${tags}\")";
  public static final String TAGS_PLACEHOLDER = "${tags}";
  private final GenerateDriverProperties props;

  private final Log log;

  public DriverGenerator(final GenerateDriverProperties props, Log log) {
    this.props = props;
    this.log = log;
  }

  public void generateDriverForFeatureFiles(final List<String> files) throws IOException {
    createTargetFolderIfNotExists();

    int ctr = 1;
    for (final String featureFile : files) {
      createTestDriverSourceFile(ctr++, featureFile);
    }
  }

  private void createTargetFolderIfNotExists() throws IOException {
    if (props.getOutputFolder().toFile().exists()) {
      FileUtils.deleteDirectory(props.getOutputFolder().toFile());
    }
    Files.createDirectories(props.getOutputFolderToPackage());
  }

  private void createTestDriverSourceFile(final int ctr, final String featurePath)
      throws IOException {
    final String currentDriverClassName =
        props.getDriverClassName().replace(COUNTER_REPLACEMENT_TOKEN, String.format("%03d", ctr));

    final String driverSourceCode =
        getDriverClassSourceCodeAsString(ctr, featurePath, currentDriverClassName);
    final String filePath = writeToDriverSourceFile(currentDriverClassName, driverSourceCode);
    log.info(" Feature '" + featurePath + "'");
    log.info(" Java => '" + filePath + "'");
  }

  private String getDriverClassSourceCodeAsString(
      final int ctr, final String featurePath, final String currentDriverClassName)
      throws IOException {
    final String packageLine =
        StringUtils.isBlank(props.getDriverPackage())
            ? ""
            : "package " + props.getDriverPackage() + ";\n";

    var templateWithReplacements =
        getTemplate()
            .replace(COUNTER_REPLACEMENT_TOKEN, String.valueOf(ctr))
            .replace("${package}", packageLine)
            .replace("${driverClassName}", currentDriverClassName)
            .replace("${feature}", featurePath.replace("\\", "/"))
            .replace("${gluesCsv}", props.getGluesCsv());

    return replaceTagAnnotationAndTags(templateWithReplacements);
  }

  private String replaceTagAnnotationAndTags(String template) {
    String tagsAnnotationReplacement;
    var cucumberFilterTags = props.getCucumberFilterTags();

    if (cucumberFilterTags != null) {
      // replace the default tags in our TAGS_ANNOTATION
      tagsAnnotationReplacement = TAGS_ANNOTATION.replace(TAGS_PLACEHOLDER, cucumberFilterTags);
      // Replace any custom ${tags} that are in a custom template
      template = template.replace(TAGS_PLACEHOLDER, cucumberFilterTags);
    } else {
      tagsAnnotationReplacement = "";
    }

    var templateWithReplacement = template.replace("${tagsAnnotation}", tagsAnnotationReplacement);

    // If any ${tags} are left in the template, throw an exception
    assertTemplateNoUndefinedTags(templateWithReplacement);

    return templateWithReplacement;
  }

  private void assertTemplateNoUndefinedTags(String templateWithReplacements) {
    if (templateWithReplacements.contains(TAGS_PLACEHOLDER)) {
      throw new IllegalArgumentException(
          "Template contains ${tags} placeholders but no replacement was found for it. Consider"
              + " using the placeholder ${tagsAnnotation} which allows for an empty tags"
              + " configuration.");
    }
  }

  private String writeToDriverSourceFile(
      final String currentDriverClassName, final String driverSourceCode) throws IOException {
    final Path sourceFile =
        props.getOutputFolderToPackage().resolve(currentDriverClassName + ".java");
    Files.write(
        sourceFile,
        driverSourceCode.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
    return sourceFile.toFile().getAbsolutePath();
  }

  private String getTemplate() throws IOException {
    if (props.getTemplateFile() == null) {
      String driverTemplateResource = "/driver5ClassTemplate.jtmpl";
      try (final InputStream is = getClass().getResourceAsStream(driverTemplateResource)) {
        if (is == null) {
          throw new FileNotFoundException(
              "Unable to find template file resource '/driver5ClassTemplate.jtmpl' in jar!");
        }
        log.debug("Using template file from jar ressource");
        return IOUtils.toString(is, StandardCharsets.UTF_8);
      }
    } else {
      log.info("Using template file '" + props.getTemplateFile() + "'");
      return Files.readString(props.getTemplateFile(), StandardCharsets.UTF_8);
    }
  }
}
