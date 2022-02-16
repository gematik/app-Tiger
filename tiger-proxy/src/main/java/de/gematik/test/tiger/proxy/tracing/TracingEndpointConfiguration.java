package de.gematik.test.tiger.proxy.tracing;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
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

    private final TigerProxyConfiguration tigerProxyConfiguration;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes(tigerProxyConfiguration.getTrafficEndpointConfiguration().getStompTopic());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        RequestUpgradeStrategy upgradeStrategy = new TomcatRequestUpgradeStrategy();

        registry.addEndpoint(tigerProxyConfiguration.getTrafficEndpointConfiguration().getWsEndpoint())
            .withSockJS();

        registry.addEndpoint(tigerProxyConfiguration.getTrafficEndpointConfiguration().getWsEndpoint())
            .setHandshakeHandler(new DefaultHandshakeHandler(upgradeStrategy))
            .setAllowedOrigins("*");
    }
}
