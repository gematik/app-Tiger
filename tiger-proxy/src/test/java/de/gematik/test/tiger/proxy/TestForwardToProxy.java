package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import java.util.List;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.TestSocketUtils;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@Slf4j
class TestForwardToProxy extends AbstractTigerProxyTest {

  private final int freePort = TestSocketUtils.findAvailableTcpPort();
  
  @SneakyThrows
  @Test
  void checkNoProxyHosts() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://hostWithoutProxying")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .forwardToProxy(
                ForwardProxyInfo.builder()
                    .port(freePort)
                    .hostname("localhost")
                    .noProxyHosts(List.of("localhost"))
                    .build())
            .build());

    final HttpResponse<JsonNode> response = proxyRest.get("http://hostWithoutProxying/ok").asJson();

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @SneakyThrows
  @Test
  void checkNoProxyHostsFromSystem() {
    new EnvironmentVariables("http_proxy", "http://localhost:" + freePort)
      .and("no_proxy", "localhost")
        .execute(
            () -> {
              spawnTigerProxyWith(
                  TigerProxyConfiguration.builder()
                      .proxyRoutes(
                          List.of(
                              TigerRoute.builder()
                                  .from("http://hostWithoutProxying")
                                  .to("http://localhost:" + fakeBackendServerPort)
                                  .build()))
                      .forwardToProxy(ForwardProxyInfo.builder().hostname("$SYSTEM").build())
                      .build());

              final HttpResponse<JsonNode> response =
                  proxyRest.get("http://hostWithoutProxying/ok").asJson();

              assertThat(response.getStatus()).isEqualTo(200);
            });
  }
}
