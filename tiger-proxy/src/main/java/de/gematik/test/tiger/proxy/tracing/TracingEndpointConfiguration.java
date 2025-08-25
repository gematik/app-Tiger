/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy.tracing;

import static de.gematik.rbellogger.util.MemoryConstants.MB;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class TracingEndpointConfiguration
    implements WebSocketMessageBrokerConfigurer, ApplicationListener<ContextStoppedEvent> {

  private final TigerProxyConfiguration tigerProxyConfiguration;
  private final List<ThreadPoolTaskExecutor> taskExecutors = new ArrayList<>();
  private final List<ThreadPoolTaskScheduler> schedulers = new ArrayList<>();

  private static ThreadPoolTaskExecutor getThreadPoolTaskExecutor() {
    final ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
    threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
    threadPoolTaskExecutor.setAwaitTerminationSeconds(2);
    threadPoolTaskExecutor.setCorePoolSize(4);
    threadPoolTaskExecutor.setMaxPoolSize(10);
    threadPoolTaskExecutor.initialize();
    return threadPoolTaskExecutor;
  }

  private static ThreadPoolTaskScheduler getThreadPoolTaskScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("TGR_scheduler-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(2);
    scheduler.setPoolSize(4);
    scheduler.initialize();
    return scheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");
    config.setApplicationDestinationPrefixes(
        tigerProxyConfiguration.getTrafficEndpointConfiguration().getStompTopic());
    config.configureBrokerChannel().taskExecutor(getThreadPoolTaskExecutor());
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint(tigerProxyConfiguration.getTrafficEndpointConfiguration().getWsEndpoint())
        .withSockJS()
        .setTaskScheduler(getThreadPoolTaskScheduler());

    registry
        .addEndpoint(tigerProxyConfiguration.getTrafficEndpointConfiguration().getWsEndpoint())
        .setHandshakeHandler(new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()))
        .setAllowedOrigins("*")
        .withSockJS()
        .setTaskScheduler(getThreadPoolTaskScheduler());

    registry
        .addEndpoint("/newMessages")
        .setHandshakeHandler(new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()))
        .withSockJS()
        .setTaskScheduler(getThreadPoolTaskScheduler());
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration.taskExecutor(getThreadPoolTaskExecutor());
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.taskExecutor(getThreadPoolTaskExecutor());
  }

  @Override
  public void onApplicationEvent(ContextStoppedEvent event) {
    taskExecutors.forEach(ThreadPoolTaskExecutor::shutdown);
    schedulers.forEach(ThreadPoolTaskScheduler::shutdown);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    log.info(
        "Configuring WebSocket transport with buffer size limit: {} MB",
        tigerProxyConfiguration.getStompClientBufferSizeInMb());
    registration.setSendBufferSizeLimit(
        tigerProxyConfiguration.getStompClientBufferSizeInMb() * MB);
  }
}
