/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.controller.TigerWebUiController;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
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
        TigerProxyRoute.builder()
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
