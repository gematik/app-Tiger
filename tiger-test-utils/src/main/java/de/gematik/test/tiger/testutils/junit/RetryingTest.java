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
 * Retries the test up to {@code maxAttempts} times, stopping as soon as one attempt passes.
 *
 * <p>Each attempt is shown as a separate entry in IntelliJ's test tree. Skipped attempts (after a
 * pass) do not appear in the tree.
 *
 * <p>Usage:
 *
 * <pre>
 * &#64;RetryingTest(maxAttempts = 3)
 * void flakyTest() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TestTemplate
@ExtendWith(RetryingTestExtension.class)
public @interface RetryingTest {

  /** Maximum number of attempts (including the first). Default: 3. */
  int maxAttempts() default 3;
}
