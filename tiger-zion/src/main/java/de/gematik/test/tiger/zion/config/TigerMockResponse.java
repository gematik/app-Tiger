package de.gematik.test.tiger.zion.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private List<String> requestCriterions;
    private TigerMockResponseDescription response;
    private Map<String, TigerMockResponse> nestedResponses = new HashMap<>();
    private Map<String, ZionBackendRequestDescription> backendRequests = new HashMap<>();
    private Map<String, String> assignments;
    private int importance = 0;
}
