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
