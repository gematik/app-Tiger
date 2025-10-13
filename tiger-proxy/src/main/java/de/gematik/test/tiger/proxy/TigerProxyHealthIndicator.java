/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import java.time.LocalDateTime;
import java.util.Optional;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import kong.unirest.core.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component("messageQueue")
@Slf4j
public class TigerProxyHealthIndicator implements HealthIndicator {

  private final TigerProxy tigerProxy;

  private Optional<LocalDateTime> lastSuccessfulRequest = Optional.empty();
  private Optional<LocalDateTime> firstFailedRequest = Optional.empty();

  public TigerProxyHealthIndicator(TigerProxy tigerProxy) {
    this.tigerProxy = tigerProxy;
  }

  @Override
  public Health health() {
    Status status = checkProxyAlive();
    long bufferSize = tigerProxy.getRbelLogger().getRbelConverter().getCurrentBufferSize();
    return Health.status(status)
        .withDetail("tigerProxyHealthy", tigerProxyHealthy())
        .withDetail("rbelMessages", tigerProxy.getRbelLogger().getMessageHistory().size())
        .withDetail("rbelMessageBuffer", bufferSize)
        .withDetail("lastSuccessfulMockserverRequest", lastSuccessfulRequest)
        .withDetail("firstFailedMockserverRequest", firstFailedRequest)
        .build();
  }

  private Status checkProxyAlive() {
    if (tigerProxy.isShuttingDown()) {
      return Status.DOWN;
    }

    // skip health check if direct reverse proxy is configured
    if (tigerProxy.getTigerProxyConfiguration().getDirectReverseProxy() != null) {
      return Status.UP;
    }

    LocalDateTime timestamp = LocalDateTime.now();
    try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().connectTimeout(2000);
      unirestInstance.config().requestTimeout(2000);
      unirestInstance.config().retryAfter(false);
      unirestInstance
          .get(
              "http://tiger.proxy/?healthEndPointUuid=" + tigerProxy.getHealthEndpointRequestUuid())
          .asString();
      lastSuccessfulRequest = Optional.of(timestamp);
      firstFailedRequest = Optional.empty();
      return Status.UP;
    } catch (UnirestException rte) {
      if (firstFailedRequest.isEmpty()) {
        firstFailedRequest = Optional.of(timestamp);
      }
      return Status.DOWN;
    }
  }

  private boolean tigerProxyHealthy() {
    return firstFailedRequest.isEmpty() && lastSuccessfulRequest.isPresent();
  }
}
