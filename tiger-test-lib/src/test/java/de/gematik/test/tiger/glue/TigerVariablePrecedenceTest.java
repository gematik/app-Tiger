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
package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.Test;

class TigerVariablePrecedenceTest {

  private final TigerGlue tigerGlue = new TigerGlue();

  @Test
  void settingLocalTestVariable_shouldBeAvailableInsteadOfGlobal() {
    // we pretend the variable is read from an yaml.
    TigerGlobalConfiguration.putValue(
        "test.hello", "global", ConfigurationValuePrecedence.MAIN_YAML);
    assertThat(TigerGlobalConfiguration.readString("test.hello")).isEqualTo("global");
    // We set a local variable
    tigerGlue.ctxtISetLocalVariableTo("test.hello", "local");
    assertThat(TigerGlobalConfiguration.readString("test.hello")).isEqualTo("local");
    // We clear the variables - this will be automatically done when running a feature file with
    // tiger - See TigerGlueTest_firstFile.feature
    TigerGlobalConfiguration.clearLocalTestVariables();
    assertThat(TigerGlobalConfiguration.readString("test.hello")).isEqualTo("global");
  }

  @Test
  void settingLocalVariable_shouldBeAvailableInsteadOfGlobal() {
    // we pretend the variable is read from an yaml.
    TigerGlobalConfiguration.putValue(
        "test.hello", "global", ConfigurationValuePrecedence.MAIN_YAML);
    assertThat(TigerGlobalConfiguration.readString("test.hello")).isEqualTo("global");
    // We set a local variable
    tigerGlue.setFeatureVariable("test.hello", "local feature");
    assertThat(TigerGlobalConfiguration.readString("test.hello")).isEqualTo("local feature");
    // We clear the variables - this will be automatically done when running a feature file with
    // tiger - See TigerGlueTest_firstFile.feature
    TigerGlobalConfiguration.clearTestVariables();
    assertThat(TigerGlobalConfiguration.readString("test.hello")).isEqualTo("global");
  }
}
