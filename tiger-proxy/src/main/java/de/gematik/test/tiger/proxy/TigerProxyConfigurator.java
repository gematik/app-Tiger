/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.proxy.controller.TigerWebUiController;
import de.gematik.test.tiger.proxy.tracing.TracingPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TigerProxyConfigurator
    implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

  private TigerProxy tigerProxy;
  private TracingPushService tracingPushService;
  private final SimpMessagingTemplate template;
  private final ServletWebServerApplicationContext webServerAppCtxt;
  private final TigerProxyConfiguration tigerProxyConfiguration;
  private final TigerWebUiController tigerWebUiController;

  @Bean
  public TigerProxy tigerProxy() {
    var shouldSubscribeAfterStart = !tigerProxyConfiguration.isSkipTrafficEndpointsSubscription();
    tigerProxyConfiguration.setSkipTrafficEndpointsSubscription(false);
    tigerProxy = new TigerProxy(tigerProxyConfiguration);
    tracingPushService = new TracingPushService(template, tigerProxy);
    tracingPushService.addWebSocketListener();
    tigerWebUiController.setTigerProxy(tigerProxy);
    tigerProxy.addRbelMessageListener(tigerWebUiController::informClientOfNewMessageArrival);
    if (shouldSubscribeAfterStart) {
      tigerProxy.subscribeToTrafficEndpoints();
    } else {
      tigerProxyConfiguration.setSkipTrafficEndpointsSubscription(true);
    }
    return tigerProxy;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (tigerProxy.getTigerProxyConfiguration().getAdminPort() == 0) {
      tigerProxy
          .getTigerProxyConfiguration()
          .setAdminPort(webServerAppCtxt.getWebServer().getPort());
    }
    log.info("Adding route for 'http://tiger.proxy'...");
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from("http://tiger.proxy")
            // you might be tempted to look for "server.port", but don't:
            // when it is zero (random free port) then it stays zero
            .to("http://localhost:" + webServerAppCtxt.getWebServer().getPort())
            .disableRbelLogging(true)
            .internalRoute(true)
            .build());
  }

  @Override
  public void customize(ConfigurableServletWebServerFactory factory) {
    if (tigerProxyConfiguration.getAdminPort() > 0) {
      factory.setPort(tigerProxyConfiguration.getAdminPort());
    }
  }
}
