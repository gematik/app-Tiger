/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.admin.helper.exceptions;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ioException) {
        log.error("Handling IOException", ioException);
        return new ResponseEntity<>(collectCauses(ioException), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(TigerConfigurationException.class)
    public ResponseEntity<String> handleIOException(TigerConfigurationException cfgException) {
        log.error("Handling TigerConfigurationException", cfgException);
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
