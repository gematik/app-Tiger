package de.gematik.test.tiger.zion.config;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("zion")
@Data
public class ZionConfiguration {

  private Map<String, TigerMockResponse> mockResponses = new HashMap<>();
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
