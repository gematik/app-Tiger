/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
package de.gematik.test.tiger.proxy.data;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.AbstractTigerProxyTest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
public class TestMessageMetaDataDto extends AbstractTigerProxyTest {

  @RegisterExtension
  static WireMockExtension forwardProxy =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort())
          .configureStaticDsl(true)
          .build();

  @BeforeAll
  public static void setupForwardProxy(WireMockRuntimeInfo runtimeInfo) {
    log.info("Started Forward-Proxy-Server on port {}", forwardProxy.getPort());

    forwardProxy.stubFor(
        get(urlMatching(".*"))
            .willReturn(aResponse().proxiedFrom("http://localhost:" + runtimeInfo.getHttpPort())));
  }

  @Test
  void checkMessageMetaDataDtoConversion() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest.get("http://backend/foobar").asJson();
    awaitMessagesInTiger(2);

    MessageMetaDataDto message0 =
        MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(0));
    assertThat(message0.getPath()).isEqualTo("/foobar");
    assertThat(message0.getMethod()).isEqualTo("GET");
    assertThat(message0.getResponseCode()).isNull();
    assertThat(message0.getRecipient()).isEqualTo("backend:80");
    // TODO TGR-651 wieder reaktivieren
    // assertThat(message0.getSender()).matches("(view-|)localhost:\\d*");
    assertThat(message0.getSequenceNumber()).isZero();

    MessageMetaDataDto message1 =
        MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(1));
    assertThat(message1.getPath()).isNull();
    assertThat(message1.getMethod()).isNull();
    assertThat(message1.getResponseCode()).isEqualTo(666);
    // TODO TGR-651 wieder reaktivieren
    // assertThat(message1.getRecipient()).matches("(view-|)localhost:\\d*");
    assertThat(message1.getSender()).isEqualTo("backend:80");
    assertThat(message1.getSequenceNumber()).isEqualTo(1);
  }
}
