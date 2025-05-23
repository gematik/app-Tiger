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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class GenerateDriverMojoTest {

  @Test
  void testEmptyIncludes_NOK() {
    final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
    mavenGoal.setIncludes(new ArrayList<>());
    assertThatThrownBy(mavenGoal::execute).isInstanceOf(MojoExecutionException.class);
  }

  @Test
  void testEmptyGlues_NOK() {
    final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
    mavenGoal.setGlues(new ArrayList<>());
    assertThatThrownBy(mavenGoal::execute).isInstanceOf(MojoExecutionException.class);
  }

  @Test
  void testDriverClassNameNoCtr_NOK() {
    final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
    mavenGoal.setDriverClassName("TestDriverClassName");
    assertThatThrownBy(mavenGoal::execute).isInstanceOf(MojoExecutionException.class);
  }

  @Test
  void testSkip() throws IOException, MojoExecutionException {
    final File folder = Paths.get("target", "generated-test-sources/tigerbdd").toFile();
    FileUtils.deleteDirectory(folder);
    final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
    mavenGoal.setSkip(true);
    mavenGoal.execute();
    assertThat(folder).doesNotExist();
  }
}
