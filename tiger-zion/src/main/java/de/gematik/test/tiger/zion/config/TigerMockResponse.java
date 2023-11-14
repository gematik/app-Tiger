package de.gematik.test.tiger.zion.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
public class TigerMockResponse {

  @TigerSkipEvaluation @Builder.Default private List<String> requestCriterions = new ArrayList<>();
  private ZionRequestMatchDefinition request;
  private TigerMockResponseDescription response;
  private Map<String, TigerMockResponse> nestedResponses = new HashMap<>();
  private Map<String, ZionBackendRequestDescription> backendRequests = new HashMap<>();
  @TigerSkipEvaluation @Builder.Default private Map<String, String> assignments = new HashMap<>();
  private int importance = 0;

  @JsonIgnore
  public Optional<ZionRequestMatchDefinition> getRequestOptional() {
    return Optional.ofNullable(request);
  }
}
