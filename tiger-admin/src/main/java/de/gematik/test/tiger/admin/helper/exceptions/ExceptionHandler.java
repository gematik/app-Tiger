package de.gematik.test.tiger.admin.helper.exceptions;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.io.IOException;

@ControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(IOException.class)
    public ResponseEntity<?> handleIOException(IOException ioException) {
        return new ResponseEntity(extractDetailedMessage(ioException, "; nested exception is", "(through reference chain:"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String extractDetailedMessage(Throwable e, String firstSequence, String secondSequence) {
        String message = e.getMessage();
        if (message == null) {
            return "";
        }
        int firstTailIndex = StringUtils.indexOf(message, firstSequence);
        if (firstTailIndex == -1) {
            int secondTailIndex = StringUtils.indexOf(message, secondSequence);
            return StringUtils.left(message, secondTailIndex);
        }
        return StringUtils.left(message, firstTailIndex);
    }
}
