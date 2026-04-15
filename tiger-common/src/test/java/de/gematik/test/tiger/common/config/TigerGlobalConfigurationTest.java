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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void testPutAndReadStringWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("testKey");
        TigerGlobalConfiguration.putValue(key, "testValue");
        assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("testValue");
        assertThat(TigerGlobalConfiguration.readStringOptional(key)).contains("testValue");
        assertThat(TigerGlobalConfiguration.readString(key, "default")).isEqualTo("testValue");
        assertThat(TigerGlobalConfiguration.readStringOptional(new TigerConfigurationKey("notSet"))).isEmpty();
        assertThat(TigerGlobalConfiguration.readString(new TigerConfigurationKey("notSet"), "default")).isEqualTo("default");
    }

    @Test
    void testPutAndReadBooleanWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("boolKey");
        TigerGlobalConfiguration.putValue(key, true);
        assertThat(TigerGlobalConfiguration.readBoolean(key)).isTrue();
        assertThat(TigerGlobalConfiguration.readBooleanOptional(key)).contains(true);
        assertThat(TigerGlobalConfiguration.readBoolean(key, false)).isTrue();
        assertThat(TigerGlobalConfiguration.readBoolean(new TigerConfigurationKey("notSet"), true)).isTrue();
        assertThat(TigerGlobalConfiguration.readBooleanOptional(new TigerConfigurationKey("notSet"))).isEmpty();
    }

    @Test
    void testPutAndReadListWithTigerConfigurationKey() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            """
                some.key:
                      - fooBar1
                      - fooBar2
                      - fooBar3
                """);
        TigerConfigurationKey key = new TigerConfigurationKey("some.key");
        assertThat(TigerGlobalConfiguration.readList(key))
            .contains("fooBar1", "fooBar2", "fooBar3");
    }

    @Test
    void testPutAndReadMapWithTigerConfigurationKey() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            """
                key:
                  select:
                    akey: aValue
                    bkey: bValue\
                """);
        final TigerConfigurationKey key = new TigerConfigurationKey("key.select");
        Map<String, String> map = TigerGlobalConfiguration.readMapByKey(key);
        assertThat(map).containsEntry("akey", "aValue");
        assertThat(map).containsEntry("bkey", "bValue");
    }

    @Test
    void testPutAndReadByteArrayWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("byteKey");
        String value = java.util.Base64.getEncoder().encodeToString("abc".getBytes());
        TigerGlobalConfiguration.putValue(key, value);
        Optional<byte[]> bytes = TigerGlobalConfiguration.readByteArray(key);
        assertThat(bytes).isPresent();
        assertThat(new String(bytes.get())).isEqualTo("abc");
    }

    @Test
    void readMapWithCaseSensitiveKeys_withTigerConfigurationKey_shouldReturnMapWithOriginalKeyCasing() {
        TigerGlobalConfiguration.readFromYaml(
            """
                anotherSection:
                  Foo: bar
                  foo: baz
                """);
        TigerConfigurationKey key = new TigerConfigurationKey("anotherSection");
        Map<String, String> result = TigerGlobalConfiguration.readMapWithCaseSensitiveKeys(key);
        assertThat(result.values()).containsAnyOf("bar", "baz");
    }

    @Test
    void testReadStringWithoutResolvingWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("unresolvedKey");
        TigerGlobalConfiguration.putValue(key, "${someOtherKey}");
        assertThat(TigerGlobalConfiguration.readStringWithoutResolving(key))
            .contains("${someOtherKey}");
    }

    @Test
    void testPutValueLongWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("longKey");
        TigerGlobalConfiguration.putValue(key, 42L);
        assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("42");
    }

    @Test
    void testPutValueDoubleWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("doubleKey");
        TigerGlobalConfiguration.putValue(key, 3.14);
        assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("3.14");
    }

    @Test
    void testPutValueIntWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("intKey");
        TigerGlobalConfiguration.putValue(key, 7);
        assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("7");
    }

    @Test
    void testPutValueObjectWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("objectKey");
        TigerGlobalConfiguration.putValue(key, (Object) "objectValue");
        assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("objectValue");
    }

    @Test
    void testPutValueWithPrecedenceWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("precedenceKey");
        TigerGlobalConfiguration.putValue(key, "lowPrio", ConfigurationValuePrecedence.TEST_YAML);
        TigerGlobalConfiguration.putValue(key, "highPrio", ConfigurationValuePrecedence.CLI);
        // CLI hat höhere Precedence, daher sollte highPrio gewonnen haben
        assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("highPrio");
    }

    @Test
    void testPutValueObjectWithPrecedenceWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("objectPrecedenceKey");
        TigerGlobalConfiguration.putValue(key, (Object) "myValue", ConfigurationValuePrecedence.TEST_YAML);
        assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("myValue");
    }

    @Test
    void testReadIntegerOptionalWithTigerConfigurationKey() {
        TigerConfigurationKey key = new TigerConfigurationKey("intOptKey");
        TigerGlobalConfiguration.putValue(key, 99);
        assertThat(TigerGlobalConfiguration.readIntegerOptional(key)).contains(99);
        assertThat(TigerGlobalConfiguration.readIntegerOptional(new TigerConfigurationKey("notSet"))).isEmpty();
    }
}
