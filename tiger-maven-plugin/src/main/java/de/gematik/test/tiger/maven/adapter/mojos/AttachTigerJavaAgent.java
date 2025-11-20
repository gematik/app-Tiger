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

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This mojo will modify the argLine property as to attach the Tiger Java Agent */
@Data
@EqualsAndHashCode(callSuper = true)
@Mojo(name = "attach-tiger-agent", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class AttachTigerJavaAgent extends AbstractMojo {

  private static final Logger log = LoggerFactory.getLogger(AttachTigerJavaAgent.class);

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
    final String oldArgLine = getProject().getProperties().getProperty("argLine");
    final String newArgLine =
        Stream.of(agentAttachOption, oldArgLine)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(" "));
    getProject().getProperties().setProperty("argLine", newArgLine);
    getLog().info("Agent attached: " + agentAttachOption);
  }
}
