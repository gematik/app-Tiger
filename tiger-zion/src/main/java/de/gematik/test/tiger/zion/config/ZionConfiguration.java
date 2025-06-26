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

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties("zion")
@Data
@Validated
public class ZionConfiguration {

  @Valid private Map<String, TigerMockResponse> mockResponses = new HashMap<>();
  private Map<String, String> mockResponseFiles = new HashMap<>();
  private ZionSpyConfiguration spy;
  private int serverPort;

  private String serverName = "zionServer";

  private String localTigerProxy;

  @PostConstruct
  public void init() {
    if (mockResponses != null) {
      mockResponses.forEach((k, v) -> v.setName(k));
      mockResponses.values().forEach(TigerMockResponse::init);
    }
  }
}
