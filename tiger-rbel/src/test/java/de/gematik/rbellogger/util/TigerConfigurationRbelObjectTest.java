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
