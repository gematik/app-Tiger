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
package de.gematik.test.tiger.testenvmgr.junit;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith({TigerExtension.class})
public @interface TigerTest {

  /**
   * Define a config-file to initialize the test environment. This has to be a file-path to a valid
   * tiger.yaml-file.
   *
   * @return
   */
  String cfgFilePath() default "";

  /**
   * Define the test-environment to use directly as a tiger.yaml-fragment. If both the cfgFilePath
   * and the tigerYaml are defined the properties defined in the tigerYaml take precedence.
   *
   * @return
   */
  String tigerYaml() default "";

  /**
   * Define additional properties to be added to the configuration. Format: "key=value"
   *
   * @return
   */
  String[] additionalProperties() default {};

  boolean skipEnvironmentSetup() default false;
}
