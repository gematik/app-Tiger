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

package de.gematik.test.tiger.maven.adapter.mojos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class AttachTigerAgentMojoTest {

  @SneakyThrows
  @Test
  void testEmptyIncludes_NOK() {
    val mojo = new AttachTigerJavaAgent();

    val artifactMock = mock(Artifact.class);
    when(artifactMock.getFile()).thenReturn(new File("tiger-java-agent.jar"));
    mojo.setPluginArtifactMap(Map.of("de.gematik.test:tiger-java-agent", artifactMock));

    val projectMock = mock(MavenProject.class);
    val properties = new Properties();
    properties.put("argLine", "-javaagent:someValue");
    when(projectMock.getProperties()).thenReturn(properties);
    mojo.setProject(projectMock);

    mojo.execute();

    assertThat(properties.getProperty("argLine")).matches("-javaagent:.* -javaagent:someValue");
  }
}
