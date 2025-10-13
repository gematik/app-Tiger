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
package de.gematik.test.tiger.testenvmgr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.UniqueId;

@Slf4j
class PathsConverterTest {

  private PathsConverter pathsConverter;
  private static final String WORKING_DIR = new File("").getAbsoluteFile().toURI().toString();
  private final UniqueId uniqueIdAbsolute1 =
      UniqueId.forEngine("cucumber")
          .append("feature", WORKING_DIR + "features/tiger-test-lib/HttpGlueCodeTest.feature")
          .append("scenario", "6");
  private final UniqueId uniqueIdRelative1 =
      UniqueId.forEngine("cucumber")
          .append("feature", "relativeFile:/features/tiger-test-lib/HttpGlueCodeTest.feature")
          .append("scenario", "6");
  private final UniqueId uniqueIdAbsolute2 =
      UniqueId.forEngine("cucumber")
          .append("feature", WORKING_DIR + "features/tiger-test-lib/HttpGlueCodeTest.feature")
          .append("scenario", "14");
  private final UniqueId uniqueIdRelative2 =
      UniqueId.forEngine("cucumber")
          .append("feature", "relativeFile:/features/tiger-test-lib/HttpGlueCodeTest.feature")
          .append("scenario", "14");

  @BeforeEach
  void setUp() {
    pathsConverter = new PathsConverter();
    log.info("working dir: " + WORKING_DIR);
    var file = new File("").toURI();
    log.info("scheme: " + file.getScheme());
    log.info("schemeSpecificpart: " + file.getSchemeSpecificPart());
    log.info("file: " + file);
  }

  @Test
  void testRelativizePaths_withFileSchemePrefix() {

    List<UniqueId> result =
        pathsConverter.relativizePaths(List.of(uniqueIdAbsolute1, uniqueIdAbsolute2));

    assertThat(result).containsExactly(uniqueIdRelative1, uniqueIdRelative2);
  }

  @Test
  void testResolvePaths_withFileSchemePrefix() {

    List<UniqueId> result =
        pathsConverter.resolvePaths(List.of(uniqueIdRelative1, uniqueIdRelative2));

    assertThat(result).containsExactly(uniqueIdAbsolute1, uniqueIdAbsolute2);
  }

  @Test
  void testConvertPath_withNonFileSchemePrefix() {
    // Test with classpath resource (no file:/ prefix)
    UniqueId uniqueId =
        UniqueId.forEngine("cucumber")
            .append("feature", "classpath:features/test.feature")
            .append("scenario", "43");

    List<UniqueId> uniqueIds = List.of(uniqueId);

    List<UniqueId> result = pathsConverter.relativizePaths(uniqueIds);

    // Should remain unchanged since it doesn't start with file:/
    assertThat(result.get(0)).isEqualTo(uniqueId);
  }

  @Test
  void testConvertPath_withNoFeatureSegment() {
    // Test with unique ID that has no feature segment
    UniqueId uniqueId = UniqueId.forEngine("cucumber").append("scenario", "49");

    List<UniqueId> uniqueIds = List.of(uniqueId);

    List<UniqueId> result = pathsConverter.relativizePaths(uniqueIds);

    // Should remain unchanged since there's no feature segment
    assertThat(result.get(0)).isEqualTo(uniqueId);
  }

  @Test
  void testUniqueIdWithNewFeatureSegment_complexScenario() {
    // Test with a more complex unique ID structure including examples
    UniqueId uniqueId =
        UniqueId.forEngine("cucumber")
            .append(
                "feature",
                "file:/C:/repos/tiger/tiger-test-lib/src/test/resources/features/tiger-test-lib/HttpGlueCodeTest.feature")
            .append("scenario", "108")
            .append("examples", "114")
            .append("example", "116");

    List<UniqueId> uniqueIds = List.of(uniqueId);

    List<UniqueId> result = pathsConverter.relativizePaths(uniqueIds);

    assertThat(result).hasSize(1);

    // Verify that all segments are preserved
    UniqueId resultId = result.get(0);
    assertThat(resultId.getSegments())
        .hasSize(5); // engine + feature + scenario + examples + example

    // Verify scenario, examples, and example segments are preserved
    assertThat(resultId.getSegments().get(2).getValue()).isEqualTo("108");
    assertThat(resultId.getSegments().get(3).getValue()).isEqualTo("114");
    assertThat(resultId.getSegments().get(4).getValue()).isEqualTo("116");
  }

  @Test
  void testEmptyList() {
    List<UniqueId> emptyList = List.of();

    List<UniqueId> result = pathsConverter.relativizePaths(emptyList);

    assertThat(result).isEmpty();
  }
}
