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
            DeprecatedKeyDescriptor.builder()
                .compareKey("foo.bar")
                .deprecatedKey("foo")
                .build(),
            "The key ('foo') in yaml file should not be used anymore! It is deprecated without a replacement!")
      );
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
