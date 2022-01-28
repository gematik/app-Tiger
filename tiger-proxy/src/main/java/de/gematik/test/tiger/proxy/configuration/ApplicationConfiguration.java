/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.configuration;

import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("tiger-proxy")
@Data
@ToString(callSuper = true)
public class ApplicationConfiguration extends TigerProxyConfiguration {

    private boolean localResources = false;
    private TigerProxyReportConfiguration report;
}
