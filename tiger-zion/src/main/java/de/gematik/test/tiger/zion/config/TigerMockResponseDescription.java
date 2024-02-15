package de.gematik.test.tiger.zion.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.zion.ZionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
public class TigerMockResponseDescription {

  @TigerSkipEvaluation private String body;
  private String bodyFile;
  @TigerSkipEvaluation @Builder.Default private Map<String, String> headers = new HashMap<>();
  @Builder.Default private Integer statusCode = 200;

  public TigerMockResponseDescription(String body, String bodyFile, Map<String, String> headers, Integer statusCode) {
    this.body = body;
    setBodyFile(bodyFile);
    this.headers = headers;
    this.statusCode = statusCode;
  }

  public void setBodyFile(String bodyFile) {
    if (bodyFile == null) {
      this.bodyFile = null;
      return;
    }
    this.bodyFile = bodyFile;
    try {
      setBody(Files.readString(Path.of(bodyFile)));
    } catch (IOException e) {
      throw new ZionException("Could not read body file '" + bodyFile + "'", e);
    }
  }
}
