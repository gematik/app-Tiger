/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.common.jexl;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.Test;

class RegisterAnnotatedJexlMethodsTest {

  /** Tests that the {@link DummyJexlMethods} are registered by calling them. */
  @Test
  void testRegisterAnnotatedJexlMethods() {
    var resultJexlExpression =
        TigerJexlExecutor.evaluateJexlExpression("test:testMethod()", new TigerJexlContext());
    assertThat(resultJexlExpression).hasValue("test");
    var resultResolvePlaceHolders =
        TigerGlobalConfiguration.resolvePlaceholders("!{test:testMethod()}");
    assertThat(resultResolvePlaceHolders).isEqualTo("test");
  }
}
