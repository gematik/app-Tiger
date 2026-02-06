/*
 * Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.assertj.core.presentation.StandardRepresentation;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy;
import org.testcontainers.utility.DockerImageName;

@Slf4j
class TestTigerProxyLegacyTraffic {
  private static final Integer DOCKER_INTERNAL_PROXY_PORT = 8080;
  private static final Integer DOCKER_INTERNAL_ADMIN_PORT = 8081;

  @Test
  void testThatNewProxyStreamsLegacyTraffic() {
    try (val oldProxyContainer = givenConfiguredOldProxy();
        val currentProxy = givenCurrentProxyConfiguredForLegacyTraffic(oldProxyContainer);
        val unirestProxied = givenUnirestClientConfiguredForOldProxy(oldProxyContainer); ) {

      val response = unirestProxied.get("http://www.example.com").asString();

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getBody()).contains("Example Domain");

      await()
          .logging()
          .atMost(20, TimeUnit.SECONDS)
          .until(() -> currentProxy.getRbelLogger().getMessages().size() >= 2);

      var messagesInCurrentProxy = currentProxy.getRbelLogger().getMessages().stream().toList();
      var messagesInOldProxy = getMessagesInOldProxy(oldProxyContainer);

      assertThat(messagesInCurrentProxy)
          .usingElementComparator(this::compareElements)
          .withRepresentation(new RbelElementRawDataAndMetadataRepresentation())
          .isEqualTo(messagesInOldProxy);

      assertThatPairedUuidsMatch(
          messagesInOldProxy.get(0),
          messagesInOldProxy.get(1),
          messagesInCurrentProxy.get(0),
          messagesInCurrentProxy.get(1));
    }
  }

  private void assertThatPairedUuidsMatch(
      RbelElement oldRequest,
      RbelElement oldResponse,
      RbelElement newRequest,
      RbelElement newResponse) {
    // previousMessage and pairedMessage metadata were not set in the old proxy. The old proxy
    // sent
    // always messages in pairs so we would now to which they match. The new proxy converts that
    // and keeps the previous and paired message metadata
    // We check that they match the original ones
    val newRequestMetadata = newRequest.getFacet(RbelMessageMetadata.class).orElseThrow();
    val newResponseMetadata = newResponse.getFacet(RbelMessageMetadata.class).orElseThrow();

    assertThat(newRequestMetadata.getPreviousMessage()).isEmpty();
    assertThat(newRequestMetadata.getPairedMessage()).hasValue(oldResponse.getUuid());
    assertThat(newResponseMetadata.getPreviousMessage()).hasValue(oldRequest.getUuid());
    assertThat(newResponseMetadata.getPairedMessage()).hasValue(oldRequest.getUuid());
  }

  public List<RbelElement> getMessagesInOldProxy(GenericContainer<?> oldProxyContainer) {
    try (val unirestNoProxy = givenUnirestClientNoProxy()) {
      val trafficLogOldProxy =
          unirestNoProxy
              .get(containerAdminUrl(oldProxyContainer) + "/webui/trafficLog-oldProxy.tgr")
              .asString();
      val rbelLogger = RbelLogger.build(new RbelConfiguration());
      val rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());

      return rbelFileWriter.convertFromRbelFile(trafficLogOldProxy.getBody(), Optional.empty());
    }
  }

  private UnirestInstance givenUnirestClientNoProxy() {
    return Unirest.spawnInstance();
  }

  private String containerAdminUrl(GenericContainer<?> container) {
    return "http://"
        + container.getHost()
        + ":"
        + container.getMappedPort(DOCKER_INTERNAL_ADMIN_PORT);
  }

  private GenericContainer<?> givenConfiguredOldProxy() {
    val oldProxyContainer =
        new GenericContainer<>(DockerImageName.parse("gematik1/tiger-proxy-image:3.7.7"));
    oldProxyContainer
        .withEnv("TIGERPROXY_PROXYPORT", String.valueOf(DOCKER_INTERNAL_PROXY_PORT))
        .withEnv("TIGERPROXY_ADMINPORT", String.valueOf(DOCKER_INTERNAL_ADMIN_PORT))
        .withEnv("MANAGEMENT_SERVER_PORT", "") // disable management server port to avoid port clash
        .withExposedPorts(DOCKER_INTERNAL_PROXY_PORT, DOCKER_INTERNAL_ADMIN_PORT)
        .withLogConsumer(new Slf4jLogConsumer(log))
        .waitingFor(new DockerHealthcheckWaitStrategy());
    oldProxyContainer.start();
    return oldProxyContainer;
  }

  private UnirestInstance givenUnirestClientConfiguredForOldProxy(
      GenericContainer<?> oldProxyContainer) {
    UnirestInstance unirest = Unirest.spawnInstance();
    unirest
        .config()
        .proxy(
            oldProxyContainer.getHost(),
            oldProxyContainer.getMappedPort(DOCKER_INTERNAL_PROXY_PORT));
    return unirest;
  }

  private TigerProxy givenCurrentProxyConfiguredForLegacyTraffic(
      GenericContainer<?> oldProxyContainer) {
    val tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .name("current_local_proxy")
                .enableLegacyTraffic(true)
                .trafficEndpoints(List.of(containerAdminUrl(oldProxyContainer)))
                .build());
    // Subscribing is not done in the constructor, but in the TigerProxyConfigurator in the spring
    // context
    // Since we are not using autowiring in this test, we have to call it manually here
    tigerProxy.subscribeToTrafficEndpoints();
    return tigerProxy;
  }

  /**
   * Compares two rbel elements. The focus here is to check if data that goes through the traffic
   * endpoint got transmitted correctly
   *
   * <p>Note: this comparator imposes orderings that are inconsistent with equals.
   *
   * @param elementA
   * @param elementB
   * @return
   */
  private int compareElements(RbelElement elementA, RbelElement elementB) {
    log.info(
        "comparing elements {} and {}",
        elementA.printTreeStructure(),
        elementB.printTreeStructure());
    val areEqual =
        Objects.equals(elementA.getUuid(), elementB.getUuid())
            && Arrays.equals(elementA.getRawContent(), elementB.getRawContent())
            && equalMetadata(elementA, elementB);

    return areEqual ? 0 : -1;
  }

  private boolean equalMetadata(RbelElement elementA, RbelElement elementB) {
    val metadataA = elementA.getFacet(RbelMessageMetadata.class).orElse(null);
    val metadataB = elementB.getFacet(RbelMessageMetadata.class).orElse(null);

    if (metadataA == null || metadataB == null) {
      return metadataA == metadataB;
    }

    return Objects.equals(metadataA.getSender(), metadataB.getSender())
        && Objects.equals(metadataA.getReceiver(), metadataB.getReceiver())
        && Objects.equals(metadataA.getTransmissionTime(), metadataB.getTransmissionTime());
  }

  private class RbelElementRawDataAndMetadataRepresentation extends StandardRepresentation {

    @Override
    public String toStringOf(Object object) {
      if (object instanceof RbelElement element) {
        return "RbelElement{"
            + "uuid="
            + element.getUuid()
            + ", rawData="
            + element.getRawStringContent()
            + ", metadata="
            + element
                .getFacet(RbelMessageMetadata.class)
                .map(this::toStringOf)
                .orElse("<no-metadata>")
            + "}";
      } else {
        return super.toStringOf(object);
      }
    }

    private String toStringOf(RbelMessageMetadata metadata) {
      return "RbelMessageMetadata{"
          + "sender="
          + metadata.getSender().orElse(null)
          + ", receiver="
          + metadata.getReceiver().orElse(null)
          + ", transmissionTime="
          + metadata.getTransmissionTime().orElse(null)
          + "}";
    }
  }
}
