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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class DeprecatedKeysUsageCheckerTest {

  public static Stream<Arguments> provideDeprecatedKeyExamples() {
    return Stream.of(
        Arguments.of(
            "foo.bar",
            DeprecatedKeyDescriptor.builder()
                .compareKey("foo.bar")
                .deprecatedKey("bar")
                .newKey("baz")
                .build(),
            "The key ('bar') in yaml file should not be used anymore, use 'baz' instead!"),
        Arguments.of(
            "foo.bar",
            DeprecatedKeyDescriptor.builder()
                .compareKey("foo.bar")
                .deprecatedKey("foo")
                .newKey("fooze")
                .build(),
            "The key ('foo') in yaml file should not be used anymore, use 'fooze' instead!"),
        Arguments.of(
            "foo.blub.bar",
            DeprecatedKeyDescriptor.builder()
                .compareKey("foo.*.bar")
                .deprecatedKey("bar")
                .newKey("baz")
                .build(),
            "The key ('bar') in yaml file should not be used anymore, use 'baz' instead!"),
        Arguments.of(
            "foo.bar.blub",
            DeprecatedKeyDescriptor.builder()
                .compareKey("foo.bar")
                .deprecatedKey("bar")
                .newKey("baz")
                .build(),
            "The key ('bar') in yaml file should not be used anymore, use 'baz' instead!"),
        Arguments.of(
            "foo.bar",
            DeprecatedKeyDescriptor.builder().compareKey("foo.bar").deprecatedKey("foo").build(),
            "The key ('foo') in yaml file should not be used anymore! It is deprecated without a"
                + " replacement!"));
  }

  @ParameterizedTest
  @MethodSource("provideDeprecatedKeyExamples")
  void checkDeprecatedKeys(
      String keyUsed, DeprecatedKeyDescriptor deprecatedKeyDescriptor, String errorMessage) {
    ReflectionTestUtils.setField(
        DeprecatedKeysUsageChecker.class, "deprecatedKeys", List.of(deprecatedKeyDescriptor));

    assertThatThrownBy(
            () ->
                DeprecatedKeysUsageChecker.checkForDeprecatedKeys(
                    Map.of(new TigerConfigurationKey(keyUsed), "value")))
        .isInstanceOf(TigerConfigurationException.class)
        .hasMessageContaining(errorMessage);
  }
}
