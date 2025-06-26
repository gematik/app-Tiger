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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.zion.ZionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
@ValidateMockResponse
public class TigerMockResponseDescription {

  @TigerSkipEvaluation private String body;
  private String bodyFile;
  @TigerSkipEvaluation @Builder.Default private Map<String, String> headers = new HashMap<>();
  @TigerSkipEvaluation @Builder.Default private String statusCode = "200";
  @TigerSkipEvaluation @Builder.Default private String responseDelay = "";
  private String encoding;

  public byte[] retrieveBodyData() {
    if (StringUtils.isNotEmpty(body)) {
      return body.getBytes();
    } else if (StringUtils.isNotEmpty(bodyFile)) {
      try {
        return Files.readAllBytes(Path.of(bodyFile));
      } catch (IOException e) {
        throw new ZionException("Could not read body file '" + bodyFile + "'", e);
      }
    } else {
      return new byte[0];
    }
  }
}
