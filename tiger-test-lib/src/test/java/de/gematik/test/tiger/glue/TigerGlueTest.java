/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import org.junit.jupiter.api.Test;

@TigerTest
class TigerGlueTest {

  private final TigerGlue tigerGlue = new TigerGlue();

  @Test
  void settingLocalTestVariable_shouldBeAvailableInsteadOfGlobal() {
    // we pretend the variable is read from an yaml.
    TigerGlobalConfiguration.putValue("test.hello", "global", SourceType.MAIN_YAML);
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
    TigerGlobalConfiguration.putValue("test.hello", "global", SourceType.MAIN_YAML);
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
