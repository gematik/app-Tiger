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
package de.gematik.test.tiger.maven.adapter;

import de.gematik.test.tiger.maven.adapter.mojos.TestEnvironmentMojo;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.mockito.Mockito;

public class MojoTestSetup {

  @SneakyThrows
  public TestEnvironmentMojo setupMojo() {
    TestEnvironmentMojo mojo = new TestEnvironmentMojo();
    MavenProject project = Mockito.mock(MavenProject.class);

    List<String> runtimeClasspathElements =
        Arrays.stream(buildClasspathUrls()).map(URL::getPath).collect(Collectors.toList());
    Mockito.when(project.getRuntimeClasspathElements()).thenReturn(runtimeClasspathElements);

    mojo.setProject(project);
    return mojo;
  }

  private static URL[] buildClasspathUrls() {
    return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
        .map(
            path -> {
              try {
                return new File(path).toURI().toURL();
              } catch (MalformedURLException e) {
                throw new RuntimeException(e);
              }
            })
        .toArray(URL[]::new);
  }
}
