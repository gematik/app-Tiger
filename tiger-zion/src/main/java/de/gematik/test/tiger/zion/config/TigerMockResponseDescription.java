package de.gematik.test.tiger.zion.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.HashMap;
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
public class TigerMockResponseDescription {

  private String body;
  private String bodyFile;
  @Builder.Default private Map<String, String> headers = new HashMap<>();
  @Builder.Default private Integer statusCode = 200;
}
