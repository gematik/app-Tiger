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
