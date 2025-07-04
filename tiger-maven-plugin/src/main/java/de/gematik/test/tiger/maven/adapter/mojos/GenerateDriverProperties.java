/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.maven.adapter.mojos;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import org.apache.maven.plugin.logging.Log;

@Builder
@Data
public class GenerateDriverProperties {

  private final List<String> glues;
  private final String driverClassName;
  private final Path templateFile;
  private final String driverPackage;
  private Path outputFolder;
  private String featuresRootFolder;
  private final String cucumberFilterTags = System.getProperty("cucumber.filter.tags");

  public void log(Log log) {
    log.info("Using features root folder: " + featuresRootFolder);
    log.info("Using output folder: " + outputFolder);
    log.debug("Using glues:" + getGluesCsv());
    log.debug("Using tags:" + getCucumberFilterTags());
  }

  public Path getOutputFolderToPackage() {
    return getOutputFolder()
        .resolve(Optional.ofNullable(getDriverPackage()).orElse("").replace(".", File.separator));
  }

  public String getGluesCsv() {
    return gluesAsCommaseparatedList(glues);
  }

  private String gluesAsCommaseparatedList(final List<String> glues) {
    Stream<String> gluesStream = glues == null ? Stream.empty() : glues.stream();
    return Stream.concat(Stream.of("de.gematik.test.tiger.glue"), gluesStream)
        .distinct()
        .collect(Collectors.joining(", "));
  }
}
