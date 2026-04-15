/*
 *  Copyright 2021-2025 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolveRelativePathToTigerYamlTest {

  @BeforeEach
  void setUp() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initialize();
  }

  @AfterEach
  void tearDown() {
    TigerGlobalConfiguration.reset();
  }

  @Test
  void relativePath_isResolvedAgainstTigerRootFolder(@TempDir Path tempDir){
    TigerConfigurationKeys.TIGER_ROOT_FOLDER.putValue(tempDir.resolve("project").toString());

    Path resolved = TigerGlobalConfiguration.resolveRelativePathToTigerYaml("target/app.jar");

    assertThat(resolved).isEqualTo(tempDir.resolve("project/target/app.jar"));
  }

  @Test
  void absolutePath_isReturnedNormalizedWithoutRebasing(@TempDir Path tempDir) {
    Path absoluteInput = tempDir.resolve("project/../project/target/app.jar");

    Path resolved =
        TigerGlobalConfiguration.resolveRelativePathToTigerYaml(absoluteInput.toString());

    assertThat(resolved).isEqualTo(absoluteInput.normalize());
  }
}
