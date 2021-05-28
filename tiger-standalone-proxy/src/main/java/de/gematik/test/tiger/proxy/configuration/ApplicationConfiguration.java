package de.gematik.test.tiger.proxy.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("tiger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationConfiguration {
    private TigerProxyConfiguration proxy;
}
