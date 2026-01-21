/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TIGER_TESTENV_CFGFILE_LOCATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
public class TigerProfileConfigurationTest {

  @SystemStub private EnvironmentVariables environmentVariables;

  @SystemStub private SystemProperties systemProperties;

  @BeforeEach
  void resetConfig() {
    TigerGlobalConfiguration.reset();
    systemProperties.set(
        TIGER_TESTENV_CFGFILE_LOCATION.getKey().toString(),
        "src/test/resources/testTigerYaml/tiger.yaml");
  }

  @AfterEach
  void resetConfigAfterTest() {
    TigerGlobalConfiguration.reset();
  }

  @Test
  void testNoProfileLeadsToDefaultConfiguration() {
    TigerGlobalConfiguration.initialize();
    val value = TigerGlobalConfiguration.readString("tiger.test");
    assertThat(value).isEqualTo("on tiger yaml");
  }

  @Test
  void testLoadsProfileConfiguredViaSystemProperty() {
    systemProperties.set("PROFILE", "profileSetViaProperties");
    TigerGlobalConfiguration.initialize();
    val value = TigerGlobalConfiguration.readString("tiger.test");
    assertThat(value).isEqualTo("set via properties");
  }

  @Test
  void testLoadsProfileConfiguredViaEnvironmentVariable() {
    environmentVariables.set("PROFILE", "profileSetViaEnv");
    TigerGlobalConfiguration.initialize();
    val value = TigerGlobalConfiguration.readString("tiger.test");
    assertThat(value).isEqualTo("set via env");
  }

  @Test
  void testThrowsExceptionWhenProfileConfiguredButFileDoesNotExist() {
    environmentVariables.set("PROFILE", "NoSuchProfileExists");
    assertThatExceptionOfType(TigerConfigurationException.class)
        .isThrownBy(TigerGlobalConfiguration::initialize)
        .withMessageContaining("Could not find profile configuration-file ");
  }
}
