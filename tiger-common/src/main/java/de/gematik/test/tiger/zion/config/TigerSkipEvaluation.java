package de.gematik.test.tiger.zion.config;

import java.lang.annotation.*;

/**
 * Used to denote to NOT evaluate a value during startup. The value might be evaluated later on.
 * This can be done to not prematurely spoil placeholder-values or JEXL-expressions.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface TigerSkipEvaluation {

}
