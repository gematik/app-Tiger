package de.gematik.test.tiger.admin.helper.exceptions;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.io.IOException;

@ControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ioException) {
        return new ResponseEntity<>(collectCauses(ioException), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(TigerConfigurationException.class)
    public ResponseEntity<String> handleIOException(TigerConfigurationException cfgException) {
        return new ResponseEntity<>(collectCauses(cfgException), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String collectCauses(Throwable e) {
        JSONObject json = new JSONObject();
        json.put("mainCause", e.getMessage());
        List<String> causes = new ArrayList<>();
        while (e.getCause() != null) {
            e = e.getCause();
            causes.add(e.getMessage());
        }
        json.put("causes", causes);
        return json.toString();
    }
}
