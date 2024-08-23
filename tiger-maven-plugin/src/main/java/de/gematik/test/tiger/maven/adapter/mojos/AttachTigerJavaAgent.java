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

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** This mojo will modify the argLine property as to attach the Tiger Java Agent */
@Data
@EqualsAndHashCode(callSuper = true)
@Mojo(name = "attach-tiger-agent", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class AttachTigerJavaAgent extends AbstractMojo {

  /** Skip running this plugin. Default is false. */
  @Parameter private boolean skip = false;

  /**
   * "Magic" property with values provided by maven. Helps us get the complete paths of artifacts
   */
  @Parameter(property = "plugin.artifactMap", required = true, readonly = true)
  private Map<String, Artifact> pluginArtifactMap;

  /** The current project representation. */
  @Parameter(property = "project", readonly = true, required = true)
  private MavenProject project;

  public AttachTigerJavaAgent() {
    super();
  }

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Skipping");
      return;
    }
    final Artifact agentArtifact = pluginArtifactMap.get("de.gematik.test:tiger-java-agent");
    if (agentArtifact == null) {
      getLog().error("Agent not found in classpath!");
      getLog().warn(pluginArtifactMap.toString());
      throw new MojoExecutionException(
          "Agent 'de.gematik.test:tiger-java-agent' not found in classpath!");
    }

    String agentAttachOption = "-javaagent:" + agentArtifact.getFile().getAbsolutePath();
    getProject().getProperties().setProperty("argLine", agentAttachOption);
    getLog().info("Agent attached: " + agentAttachOption);
  }
}
