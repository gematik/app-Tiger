package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TigerConfigurationRbelObjectTest {

  private final TigerConfigurationLoader conf = new TigerConfigurationLoader();

  @BeforeEach
  public void setUp() {
    conf.putValue("myMap.anotherLevel.key1.value", "value1");
  }

  @Test
  void findParentShouldBeCorrect() {
    val configurationValue = new TigerConfigurationRbelObject(conf);
    assertThat(configurationValue.getParentNode()).isNull();
    val refoundRoot = configurationValue.findRbelPathMembers("$.myMap").get(0).getParentNode();
    assertThat(refoundRoot).isEqualTo(configurationValue);
  }

  @Test
  void findPathForConfigurationValues_shouldWork() {
    val configurationValue = new TigerConfigurationRbelObject(conf);
    assertThat(configurationValue.findNodePath()).isEqualTo("");
    val nestedValue =
        configurationValue.findRbelPathMembers("$.myMap.anotherLevel.key1.value").get(0);
    assertThat(nestedValue.findNodePath()).isEqualTo("myMap.anotherLevel.key1.value");
  }
}
