/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@Slf4j
public class TracingpointsController {

  private final TigerProxy tigerProxy;
  private final Optional<BuildProperties> buildProperties;
  private final TigerProxyConfiguration tigerProxyConfiguration;

  @GetMapping(value = "/tracingpoints", produces = MediaType.APPLICATION_JSON_VALUE)
  public TracingpointsInfo[] getTracingpointsInfo() {
    return new TracingpointsInfo[] {
      TracingpointsInfo.builder()
          .name(tigerProxyConfiguration.getTrafficEndpointConfiguration().getName())
          .port(tigerProxy.getProxyPort())
          .wsEndpoint(tigerProxyConfiguration.getTrafficEndpointConfiguration().getWsEndpoint())
          .stompTopic(
              "/topic" + tigerProxyConfiguration.getTrafficEndpointConfiguration().getStompTopic())
          .protocolType("tigerProxyStomp")
          .protocolVersion(
              buildProperties.map(BuildProperties::getVersion).orElse("<unknown version>"))
          .serverVersion(
              buildProperties.map(BuildProperties::getVersion).orElse("<unknown version>"))
          .serverDate(
              buildProperties
                  .map(BuildProperties::getTime)
                  .map(t -> t.atZone(ZoneId.systemDefault()))
                  .orElse(null))
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
    private final String serverVersion;
    private final ZonedDateTime serverDate;
  }
}
