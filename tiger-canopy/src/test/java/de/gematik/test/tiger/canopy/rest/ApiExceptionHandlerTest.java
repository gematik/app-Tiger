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
 *
 */
package de.gematik.test.tiger.canopy.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.canopy.client.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.HandlerMethod;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void illegalArgumentMapsToBadRequest() {
    ResponseEntity<ApiErrorResponse> response =
        handler.handleIllegalArgument(new IllegalArgumentException("bad host"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ApiErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(400);
    assertThat(body.message()).isEqualTo("bad host");
  }

  @Test
  void beanValidationConcatenatesMessagesAndSubstitutesNullDefaults() throws Exception {
    BindingResult br = new BeanPropertyBindingResult(new Object(), "request");
    br.addError(new FieldError("request", "host", "must not be blank"));
    br.addError(new FieldError("request", "matchType", (String) null)); // null default → "invalid"

    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(dummyHandlerMethod().getMethodParameters()[0], br);

    ResponseEntity<ApiErrorResponse> response = handler.handleBeanValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).contains("must not be blank").contains("invalid");
  }

  @Test
  void beanValidationWithoutErrorsReturnsGenericMessage() throws Exception {
    BindingResult br = new BeanPropertyBindingResult(new Object(), "request");
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(dummyHandlerMethod().getMethodParameters()[0], br);

    ResponseEntity<ApiErrorResponse> response = handler.handleBeanValidation(ex);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("validation failed");
  }

  @Test
  void constraintViolationMessageContainsPathAndMessage() {
    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    Path path = mock(Path.class);
    when(path.toString()).thenReturn("addProxiedHost.request.host");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must not be blank");

    ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
    ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message())
        .contains("addProxiedHost.request.host")
        .contains("must not be blank");
  }

  // ---- helpers ------------------------------------------------------------

  /** Builds a HandlerMethod whose parameter array is non-empty (Spring needs index 0 to exist). */
  private static HandlerMethod dummyHandlerMethod() throws NoSuchMethodException {
    return new HandlerMethod(
        new DummyController(), DummyController.class.getDeclaredMethod("handle", List.class));
  }

  @SuppressWarnings("unused")
  private static final class DummyController {
    public void handle(List<String> ignored) {
      // no-op
    }
  }
}
