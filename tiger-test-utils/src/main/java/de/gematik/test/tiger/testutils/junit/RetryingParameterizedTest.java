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
package de.gematik.test.tiger.testutils.junit;

import java.lang.annotation.*;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Drop-in replacement for {@code @ParameterizedTest} that retries each parameter set up to {@code
 * maxAttempts} times, stopping as soon as one attempt passes.
 *
 * <p>Use with the same source annotations as {@code @ParameterizedTest}:
 *
 * <pre>
 * &#64;RetryingParameterizedTest(maxAttempts = 3)
 * &#64;MethodSource("provideArgs")
 * void test(String s, int n) { ... }
 *
 * &#64;RetryingParameterizedTest(maxAttempts = 3)
 * &#64;ValueSource(ints = {1, 2, 3})
 * void test(int n) { ... }
 *
 * &#64;RetryingParameterizedTest(maxAttempts = 3)
 * &#64;CsvSource({"a, 1", "b, 2"})
 * void test(String s, String n) { ... }
 * </pre>
 *
 * <p>Note: argument type conversion (e.g. String → int for {@code @CsvSource}) is not applied. Use
 * {@code @MethodSource} with properly typed arguments for best compatibility.
 *
 * <p>Each attempt is shown as a separate tree node in IntelliJ. Attempts stop per-parameter-set on
 * first pass.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TestTemplate
@ExtendWith(RetryingParameterizedTestExtension.class)
public @interface RetryingParameterizedTest {

  /** Maximum number of attempts per parameter set (including the first). Default: 3. */
  int maxAttempts() default 3;
}
