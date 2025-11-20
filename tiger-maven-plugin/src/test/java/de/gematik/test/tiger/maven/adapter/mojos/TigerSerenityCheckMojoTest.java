/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.maven.adapter.mojos;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import java.io.File;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TigerSerenityCheckMojoTest {

  @Mock private Log log;

  @Mock private MavenProject project;

  private TigerSerenityCheckMojo mojo;

  @TempDir File tempDir;

  @BeforeEach
  void setUp() {
    mojo = spy(TigerSerenityCheckMojo.class);
    mojo.setLog(log);
    mojo.project = project;
  }

  @Test
  void execute_shouldSkipForPomPackaging() throws MojoExecutionException, MojoFailureException {
    // Given
    when(project.getPackaging()).thenReturn("pom");
    mojo.reportDirectory = new File(tempDir, "serenity");

    // When
    mojo.execute();

    // Then
    verify(log).info("Skipping execution for POM packaging");
    verify(mojo, never()).executeSerenityCheckMojo();
  }

  @Test
  void execute_shouldSkipWhenReportDirectoryDoesNotExist()
      throws MojoExecutionException, MojoFailureException {
    // Given
    when(project.getPackaging()).thenReturn("jar");
    mojo.reportDirectory = new File(tempDir, "non-existent");

    // When
    mojo.execute();

    // Then
    verify(log).info("Report directory does not exist. Skipping report check");
    verify(mojo, never()).executeSerenityCheckMojo(); // super.execute() should not be called
  }

  @Test
  void execute_shouldCallSuperExecuteWhenReportDirectoryExists()
      throws MojoExecutionException, MojoFailureException {
    // Given
    File existingDir = new File(tempDir, "serenity");
    existingDir.mkdirs();
    mojo.reportDirectory = existingDir;
    doNothing().when(mojo).executeSerenityCheckMojo();
    // When
    mojo.execute();

    // Then
    verify(log, never()).info(anyString());
    // Verify that the overridden execute method calls super.execute()
    assertThatCode(() -> verify(mojo).executeSerenityCheckMojo()).doesNotThrowAnyException();
  }

  @Test
  void execute_shouldPropagateExceptionsFromSuperExecute()
      throws MojoExecutionException, MojoFailureException {
    // Given
    File existingDir = new File(tempDir, "serenity");
    existingDir.mkdirs();
    mojo.reportDirectory = existingDir;

    MojoExecutionException expectedException = new MojoExecutionException("Test exception");
    doThrow(expectedException).when(mojo).executeSerenityCheckMojo();

    // When/Then
    assertThatCode(() -> mojo.execute())
        .isInstanceOf(MojoExecutionException.class)
        .hasMessage("Test exception");
  }
}
