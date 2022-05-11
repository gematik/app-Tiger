/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
                .port(tigerProxy.getProxyPort())

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
