/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.common.util;

import static de.gematik.test.tiger.common.util.TigerSerializationUtil.toMap;
import static de.gematik.test.tiger.common.util.TigerSerializationUtil.yamlToJsonObject;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerConfigurationTest.DummyBean;
import de.gematik.test.tiger.common.config.TigerConfigurationTest.NestedBean;
import java.util.Map;
import lombok.val;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class TigerSerializationUtilTest {

  @Test
  void yamlToJsonObjectNestedTest() {
    final JSONObject jsonObject =
        yamlToJsonObject(
            """
                        foo:
                          bar: someString
                          list:
                            - entry
                            - schmentry
                            - nested:
                                entry: withValue
                          object:
                            with:
                              some:
                                depth: "to it"
                        """);

    assertThat(jsonObject.getJSONObject("foo").getString("bar")).isEqualTo("someString");
    assertThat(
            jsonObject
                .getJSONObject("foo")
                .getJSONObject("object")
                .getJSONObject("with")
                .getJSONObject("some")
                .getString("depth"))
        .isEqualTo("to it");
    assertThat(
            jsonObject
                .getJSONObject("foo")
                .getJSONArray("list")
                .getJSONObject(2)
                .getJSONObject("nested")
                .getString("entry"))
        .isEqualTo("withValue");
  }

  @Test
  void yamlToMapTest() {
    assertThat(
            toMap(
                DummyBean.builder()
                    .integer(42)
                    .string("stringValue")
                    .nestedBean(
                        NestedBean.builder()
                            .foo("nestedFoo")
                            .inner(NestedBean.builder().foo("nestedInnerFoo").build())
                            .build())
                    .build(),
                "dummy"))
        .containsEntry("dummy.integer", "42")
        .containsEntry("dummy.string", "stringValue")
        .containsEntry("dummy.nestedbean.foo", "nestedFoo")
        .containsEntry("dummy.nestedbean.inner.foo", "nestedInnerFoo");
  }

  @Test
  void flatMapToYamlTest_conflictingKeys() {
    val flatMap =
        Map.of(
            "java.version", "11",
            "java.version.date", "2021-04-20",
            "os", "Windows NT",
            "os.arch", "amd64",
            "os.version", "10.0");

    val expectedYaml =
        """
    java:
      version: '11'
      version.date: '2021-04-20'
    os: Windows NT
    os.arch: amd64
    os.version: '10.0'
    """;

    assertThat(TigerSerializationUtil.toNestedYaml(flatMap)).isEqualTo(expectedYaml);
  }

  @Test
  void flatMapToYamlTest_noConflictingKeys() {
    val flatMap =
        Map.of(
            "dummy.string", "stringValue",
            "dummy.nestedbean.foo", "nestedFoo",
            "dummy.nestedbean.inner.foo", "nestedInnerFoo");

    val expectedYaml =
        """
        dummy:
          nestedbean:
            foo: nestedFoo
            inner:
              foo: nestedInnerFoo
          string: stringValue
        """;

    assertThat(TigerSerializationUtil.toNestedYaml(flatMap)).isEqualTo(expectedYaml);
  }

  @Test
  void flatMapToYamlTest_listEntries() {
    val flatMap =
        Map.of(
            "dummy.0", "0th",
            "dummy.1", "1st",
            "dummy.2", "2nd");

    val expectedYaml =
        """
            dummy:
              '0': 0th
              '1': 1st
              '2': 2nd
            """;

    assertThat(TigerSerializationUtil.toNestedYaml(flatMap)).isEqualTo(expectedYaml);
  }
}
