package de.gematik.rbellogger.converter;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation for converter classes to specify when the converter should be activated.
 * If the converter is not annotated with this annotation, it will always be activated.
 * The specific keywords should be declared in the configuration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConverterInfo {

  String[] onlyActivateFor() default {};

  Class<? extends RbelConverterPlugin>[] dependsOn() default {};

  boolean addAsPostConversionListener() default false;

  boolean addAutomatically() default true;
}
