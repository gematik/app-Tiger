/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@Slf4j
public class TracingpointsController {

    private final TigerProxy tigerProxy;
    @Value("${app.version:unknown}")
    private String applicationVersion;
    private final TigerProxyConfiguration tigerProxyConfiguration;

    @GetMapping(value = "/tracingpoints", produces = MediaType.APPLICATION_JSON_VALUE)
    public TracingpointsInfo[] getTracingpointsInfo() {
        return new TracingpointsInfo[]{
            TracingpointsInfo.builder()
                .name(tigerProxyConfiguration.getTrafficEndpointConfiguration().getName())
                .port(tigerProxy.getPort())

                .wsEndpoint(tigerProxyConfiguration.getTrafficEndpointConfiguration().getWsEndpoint())
                .stompTopic("/topic" + tigerProxyConfiguration.getTrafficEndpointConfiguration().getStompTopic())

                .protocolType("tigerProxyStomp")
                .protocolVersion(applicationVersion)

                .build()
        };
    }

    @Builder
    @RequiredArgsConstructor
    @Getter
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class TracingpointsInfo {

        private final String name;
        private final int port;
        private final String wsEndpoint;
        private final String stompTopic;
        private final String protocolType;
        private final String protocolVersion;
    }
}
