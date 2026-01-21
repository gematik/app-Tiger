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

import lombok.AllArgsConstructor;
import lombok.Getter;

// TODO explain types and how precedence is used to obtain the value to a property
@AllArgsConstructor
@Getter
public enum ConfigurationValuePrecedence {
  DEFAULTS(110),
  MAIN_YAML(105),
  HOST_YAML(100),
  PROFILE_YAML(98),
  ADDITIONAL_YAML(95),
  TEST_YAML(90),
  ENV(80),
  PROPERTIES(70),
  CLI(60),
  RUNTIME_EXPORT(50),
  TEST_CONTEXT(40),
  LOCAL_TEST_CASE_CONTEXT(20);

  private final int value;
}
