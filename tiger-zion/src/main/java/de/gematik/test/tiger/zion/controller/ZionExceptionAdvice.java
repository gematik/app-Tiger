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
package de.gematik.test.tiger.zion.controller;

import de.gematik.test.tiger.zion.data.ZionErrorDto;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ZionExceptionAdvice extends ResponseEntityExceptionHandler {

  @ExceptionHandler(RuntimeException.class)
  protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
    logger.warn("Error during request processing", ex);
    return handleExceptionInternal(
        ex,
        transformExceptionToErrorDto(ex),
        new HttpHeaders(),
        HttpStatus.INTERNAL_SERVER_ERROR,
        request);
  }

  private ZionErrorDto transformExceptionToErrorDto(RuntimeException ex) {
    return ZionErrorDto.builder()
        .errorMessage(ex.getMessage())
        .errorType(ex.getClass().getSimpleName())
        .stacktrace(ExceptionUtils.getStackTrace(ex))
        .build();
  }
}
