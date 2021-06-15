package de.gematik.test.tiger.proxy.tracing;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class TracingEndpointConfiguration implements WebSocketMessageBrokerConfigurer {

    public static final String TRACING_ENDPOINT = "/tracing";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/traces");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        RequestUpgradeStrategy upgradeStrategy = new TomcatRequestUpgradeStrategy();

        registry.addEndpoint(TRACING_ENDPOINT)
            .withSockJS();

        registry.addEndpoint(TRACING_ENDPOINT)
            .setHandshakeHandler(new DefaultHandshakeHandler(upgradeStrategy))
            .setAllowedOrigins("*");
    }
}
