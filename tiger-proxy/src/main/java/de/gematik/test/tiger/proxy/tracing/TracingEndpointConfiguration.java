/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.tracing;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class TracingEndpointConfiguration implements WebSocketMessageBrokerConfigurer,
    ApplicationListener<ContextClosedEvent> {

    private final TigerProxyConfiguration tigerProxyConfiguration;
    private final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes(
            tigerProxyConfiguration.getTrafficEndpointConfiguration().getStompTopic());
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

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(taskExecutor);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        taskExecutor.shutdown();
    }
}
