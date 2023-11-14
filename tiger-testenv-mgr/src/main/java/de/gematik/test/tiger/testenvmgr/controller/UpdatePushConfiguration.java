/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class UpdatePushConfiguration implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureMessageBroker(final MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");
    config.setApplicationDestinationPrefixes("/envStatus");
    config.setApplicationDestinationPrefixes("/serverLog");
  }

  @Override
  public void registerStompEndpoints(final StompEndpointRegistry registry) {
    registry
        .addEndpoint("/testEnv")
        .setHandshakeHandler(new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()))
        .withSockJS();
    registry
        .addEndpoint("/testLog")
        .setHandshakeHandler(new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()))
        .withSockJS();
  }

  @Override
  public void configureClientOutboundChannel(final ChannelRegistration registration) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    registration.taskExecutor(executor);
  }
}
