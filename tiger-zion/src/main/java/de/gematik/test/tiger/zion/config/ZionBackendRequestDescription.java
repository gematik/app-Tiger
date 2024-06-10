package de.gematik.test.tiger.zion.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
public class ZionBackendRequestDescription {

  @TigerSkipEvaluation private String url;
  @TigerSkipEvaluation private Map<String, String> headers;
  @TigerSkipEvaluation private String body;
  @TigerSkipEvaluation private String method;
  @Builder.Default private boolean executeAfterSelection = false;
  @TigerSkipEvaluation private Map<String, String> assignments;
}
