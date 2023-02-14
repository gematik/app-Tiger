package de.gematik.test.tiger.zion.config;

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
}
