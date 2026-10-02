/*
 *
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.lib;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TigerLibConfigPcapCaptureBindingTest {

  @BeforeEach
  @AfterEach
  void resetConfig() {
    TigerGlobalConfiguration.reset();
  }

  @Test
  void defaults_areAppliedWhenNothingIsConfigured() {
    // Point away from the module's real tiger.yaml (which enables pcap capture for the
    // suite in TestPcapCapture) so this test sees the bean's actual declared defaults. Must be
    // a system property, not TigerGlobalConfiguration.putValue: the first config call of any
    // kind triggers auto-init, which reads tiger.yaml before putValue's own value would land.
    System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/minimal_tiger.yaml");
    try {
      TigerGlobalConfiguration.readFromYaml("");

      var libConfig =
          TigerGlobalConfiguration.instantiateConfigurationBean(TigerLibConfig.class, "TIGER_LIB")
              .orElseGet(TigerLibConfig::new);

      assertThat(libConfig.pcapCapture).isNotNull();
      assertThat(libConfig.pcapCapture.isEnabled()).isFalse();
      assertThat(libConfig.pcapCapture.getFilename()).isEqualTo("${scenarioId}.pcapng");
      assertThat(libConfig.pcapCapture.isSplitByTestcase()).isTrue();
      assertThat(libConfig.pcapCapture.getSnaplenKb()).isEqualTo(64);
      assertThat(libConfig.pcapCapture.getBufferSizeKb()).isEqualTo(16 * 1024);
      assertThat(libConfig.pcapCapture.isRemoteProxies()).isFalse();
    } finally {
      System.clearProperty("TIGER_TESTENV_CFGFILE");
    }
  }

  @Test
  void overrides_areReadFromTigerGlobalConfiguration() {
    TigerGlobalConfiguration.putValue("tiger.lib.pcapCapture.enabled", "true");
    TigerGlobalConfiguration.putValue("tiger.lib.pcapCapture.splitByTestcase", "false");
    TigerGlobalConfiguration.putValue("tiger.lib.pcapCapture.snaplenKb", "128");
    TigerGlobalConfiguration.putValue("tiger.lib.pcapCapture.bufferSizeKb", "32768");
    TigerGlobalConfiguration.putValue("tiger.lib.pcapCapture.filename", "suite.pcapng");

    var libConfig =
        TigerGlobalConfiguration.instantiateConfigurationBean(TigerLibConfig.class, "TIGER_LIB")
            .orElseThrow();

    assertThat(libConfig.pcapCapture.isEnabled()).isTrue();
    assertThat(libConfig.pcapCapture.isSplitByTestcase()).isFalse();
    assertThat(libConfig.pcapCapture.getSnaplenKb()).isEqualTo(128);
    assertThat(libConfig.pcapCapture.getBufferSizeKb()).isEqualTo(32768);
    assertThat(libConfig.pcapCapture.getFilename()).isEqualTo("suite.pcapng");
  }
}
