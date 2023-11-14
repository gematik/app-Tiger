/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
package de.gematik.test.tiger.config;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith({ResetTigerConfigurationExtension.class})
public @interface ResetTigerConfiguration {

  boolean beforeAllMethods() default true;

  boolean afterAllMethods() default true;
}
