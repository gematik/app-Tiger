/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package io.cucumber.core.plugin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntermediateReportGeneratorTest {

  @Test
  @DisplayName("Should handle missing report directory gracefully")
  void shouldHandleMissingReportDirectoryGracefully(@TempDir Path tempDir) {
    Path nonExistentDir = tempDir.resolve("nonexistent");
    IntermediateReportGenerator generator =
        new IntermediateReportGenerator(nonExistentDir, tempDir);

    // Should not throw
    assertThatNoException()
        .isThrownBy(
            () -> {
              generator.onFeatureCompleted(URI.create("file:test.feature"));
              generator.shutdown();
            });
  }

  @Test
  @DisplayName("Should produce a progress file after processing features with real JSON outcomes")
  void shouldProduceProgressFileWithRealOutcomes(@TempDir Path tempDir) throws Exception {
    Path reportDir = tempDir.resolve("serenity");
    Files.createDirectories(reportDir);

    Path sourceJson =
        Path.of(
            "../tiger-maven-plugin/src/test/resources/serenityReports/fresh/7ce696c3605a99c1796a8791d96109283a6c99ad1e94731b7208d02c0f3ba04b.json");
    assertThat(sourceJson).exists();

    IntermediateReportGenerator generator = new IntermediateReportGenerator(reportDir, tempDir);

    try {
      // Copy JSON file BEFORE first feature completes (simulating Serenity writing files)
      Files.copy(sourceJson, reportDir.resolve("feature1.json"));
      generator.onFeatureCompleted(URI.create("file:feature1.feature"));

      Path progressFile = reportDir.resolve("progress.json");
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertThat(progressFile).exists());
    } finally {
      generator.shutdown();
    }
  }

  @Test
  @DisplayName("Should shutdown cleanly even when no features completed")
  void shouldShutdownCleanlyWhenNoFeaturesCompleted(@TempDir Path tempDir) {
    IntermediateReportGenerator generator = new IntermediateReportGenerator(tempDir, tempDir);

    assertThatNoException().isThrownBy(generator::shutdown);
  }
}
