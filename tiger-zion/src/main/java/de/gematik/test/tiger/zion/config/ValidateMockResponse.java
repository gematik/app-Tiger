package de.gematik.test.tiger.zion.config;

import de.gematik.test.tiger.zion.config.ValidateMockResponse.MockResponseDescriptionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MockResponseDescriptionValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateMockResponse {

  String message() default "Unable to retrieve body data from TigerMockResponseDescription";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class MockResponseDescriptionValidator
      implements ConstraintValidator<ValidateMockResponse, TigerMockResponseDescription> {
    @Override
    public boolean isValid(TigerMockResponseDescription value, ConstraintValidatorContext context) {
      value.retrieveBodyData();
      return true;
    }
  }
}
