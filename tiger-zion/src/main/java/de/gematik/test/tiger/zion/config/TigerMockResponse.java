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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
@Validated
public class TigerMockResponse {

  @TigerSkipEvaluation @Builder.Default private List<String> requestCriterions = new ArrayList<>();
  private ZionRequestMatchDefinition request;
  @Valid private TigerMockResponseDescription response;
  @Builder.Default private Map<String, TigerMockResponse> nestedResponses = new HashMap<>();

  @Builder.Default
  private Map<String, ZionBackendRequestDescription> backendRequests = new HashMap<>();

  @TigerSkipEvaluation @Builder.Default private Map<String, String> assignments = new HashMap<>();
  @Builder.Default private int importance = 0;
  private String name;

  @JsonIgnore
  public Optional<ZionRequestMatchDefinition> getRequestOptional() {
    return Optional.ofNullable(request);
  }

  public void init() {
    if (nestedResponses != null) {
      nestedResponses.forEach((k, v) -> v.setName(k));
      nestedResponses.values().forEach(TigerMockResponse::init);
    }
  }
}
