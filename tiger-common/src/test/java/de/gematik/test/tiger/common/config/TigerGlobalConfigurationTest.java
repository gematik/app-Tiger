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
package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TigerGlobalConfigurationTest {

  @BeforeEach
  void resetConfig() {
    TigerGlobalConfiguration.reset();
  }

  @AfterEach
  void resetConfigAfterTest() {
    TigerGlobalConfiguration.reset();
  }

  @ParameterizedTest
  @ValueSource(strings = {"hElLo", "hello", "HELLO"})
  void loadingConfigurationBeansShouldAlsoBeCaseInsensitive(String readingWithKey) {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.putValue("hElLo", "world");
    assertThat(TigerGlobalConfiguration.readString(readingWithKey)).isEqualTo("world");

    assertThat(TigerGlobalConfiguration.instantiateConfigurationBean(String.class, readingWithKey))
        .as("reading with key " + readingWithKey)
        .hasValue("world");
  }
}
