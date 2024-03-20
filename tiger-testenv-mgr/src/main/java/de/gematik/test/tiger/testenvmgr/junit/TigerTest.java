/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
