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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.maven.adapter.mojos.TestEnvironmentMojo;
import java.net.URLClassLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TigerMavenPluginClasspathTest {

  private MojoTestSetup mojoTestSetup;

  @BeforeEach
  void setUp() {
    mojoTestSetup = new MojoTestSetup();
  }

  @Test
  void testShouldFailWhenMojoUsesIsolatedClassLoader() {
    TestEnvironmentMojo mojo = mojoTestSetup.setupMojo();

    mojo.setClassLoaderBuilder(() -> new URLClassLoader(mojo.getProjectClasspathUrls(), null));

    assertThrows(TigerStartupException.class, mojo::execute);
  }

  @Test
  void testShouldPassWhenMojoUsesCorrectClassLoader() {
    TestEnvironmentMojo mojo = mojoTestSetup.setupMojo();

    assertDoesNotThrow(mojo::execute);
  }
}
