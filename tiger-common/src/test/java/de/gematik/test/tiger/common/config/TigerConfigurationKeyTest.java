/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TigerConfigurationKeyTest {
  @Test
  void testCreateConfigurationKeyWithRepeatedSubKey_ShouldAppendToKey() {
    var baseKey =
        new TigerConfigurationKey("mockResponses", "testResponse", "nestedResponses", "login");

    assertThat(baseKey.downsampleKey())
        .isEqualTo("mockresponses.testresponse.nestedresponses.login");

    var combinedKey = new TigerConfigurationKey(baseKey, "nestedResponses");

    assertThat(combinedKey.downsampleKey())
        .isEqualTo("mockresponses.testresponse.nestedresponses.login.nestedresponses");
  }
}
