/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TigerTypedConfigurationKeyTest {

    @BeforeEach
    void setup() {
        TigerGlobalConfiguration.reset();
    }

    @Test
    void intKeyShouldBeFoundAndRead() {
        final String key = "my.int.key";
        var intKey = new TigerTypedConfigurationKey<>(key, Integer.class);
        TigerGlobalConfiguration.putValue(key, "12345");
        assertThat(intKey.getValue())
            .get()
            .isEqualTo(12345);
    }

    @Test
    void booleanKeyShouldBeFoundAndRead() {
        final String key = "my.bool.key";
        var boolKey = new TigerTypedConfigurationKey<>(key, Boolean.class);
        TigerGlobalConfiguration.putValue(key, "true");
        assertThat(boolKey.getValue())
            .get()
            .isEqualTo(true);
    }

    @Test
    void booleanKeyDefaultValue() {
        var boolKey = new TigerTypedConfigurationKey<>("key.which.is.not.set", Boolean.class, true);
        assertThat(boolKey.getValueOrDefault()).isTrue();
    }

    @Test
    void testPutValue() {
        var stringKey = new TigerTypedConfigurationKey<>("key.which.is.not.set", String.class);
        assertThat(stringKey.getValue()).isEmpty();
        stringKey.putValue("myNewValue");
        assertThat(stringKey.getValue()).get().isEqualTo("myNewValue");
    }
}
