/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import java.time.LocalDateTime;
import java.util.Optional;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.UnirestInstance;
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
    int adminPort = tigerProxy.getAdminPort();

    LocalDateTime timestamp = LocalDateTime.now();
    try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().connectTimeout(2000);
      unirestInstance.config().socketTimeout(2000);
      unirestInstance
          .get(
              "http://localhost:"
                  + adminPort
                  + "/?healthEndPointUuid="
                  + tigerProxy.getHealthEndpointRequestUuid())
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
