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
package de.gematik.test.tiger.mockserver.lifecycle;

import static de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration.configuration;
import static de.gematik.test.tiger.mockserver.mock.HttpState.clearPort;
import static de.gematik.test.tiger.mockserver.mock.HttpState.setPort;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Getter
@Slf4j
public abstract class LifeCycle {

  protected final EventLoopGroup bossGroup;
  protected final EventLoopGroup workerGroup;
  protected final HttpState httpState;
  private final MockServerConfiguration configuration;
  protected ServerBootstrap serverServerBootstrap;
  private final List<Future<Channel>> serverChannelFutures = new ArrayList<>();
  private final CompletableFuture<String> stopFuture = new CompletableFuture<>();
  private final AtomicBoolean stopping = new AtomicBoolean(false);
  private final Scheduler scheduler;

  protected LifeCycle(MockServerConfiguration configuration) {
    clearPort();
    this.configuration = configuration != null ? configuration : configuration();
    this.bossGroup =
        new NioEventLoopGroup(
            5, new Scheduler.SchedulerThreadFactory(getMockServerName() + "-bossGroup"));
    this.workerGroup =
        new NioEventLoopGroup(
            this.configuration.nioEventLoopThreadCount(),
            new Scheduler.SchedulerThreadFactory(getMockServerName() + "-workerEventLoop"));
    this.scheduler = new Scheduler(this.configuration);
    this.httpState = new HttpState(this.configuration, this.scheduler);
  }

  private String getMockServerName() {
    if (configuration.mockServerName() != null) {
      return configuration.mockServerName();
    } else {
      return this.getClass().getSimpleName();
    }
  }

  public CompletableFuture<String> stopAsync() {
    if (!stopFuture.isDone() && stopping.compareAndSet(false, true)) {
      final String message =
          "stopped for port"
              + (getLocalPorts().size() == 1
                  ? ": " + getLocalPorts().get(0)
                  : "s: " + getLocalPorts());
      log.info(message);
      new Scheduler.SchedulerThreadFactory("Stop")
          .newThread(
              () -> {
                List<ChannelFuture> collect =
                    serverChannelFutures.stream()
                        .flatMap(
                            channelFuture -> {
                              try {
                                return Stream.of(channelFuture.get());
                              } catch (InterruptedException | ExecutionException e) {
                                if (e instanceof InterruptedException) {
                                  Thread.currentThread().interrupt();
                                }
                                return Stream.empty();
                              }
                            })
                        .map(ChannelOutboundInvoker::disconnect)
                        .toList();
                try {
                  for (ChannelFuture channelFuture : collect) {
                    channelFuture.get();
                  }
                } catch (InterruptedException | ExecutionException e) {
                  if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                  }
                  log.debug("Ignoring exception", e);
                }

                scheduler.shutdown();

                // Shut down all event loops to terminate all threads.
                bossGroup.shutdownGracefully(5, 5, MILLISECONDS);
                workerGroup.shutdownGracefully(5, 5, MILLISECONDS);

                // Wait until all threads are terminated.
                bossGroup.terminationFuture().syncUninterruptibly();
                workerGroup.terminationFuture().syncUninterruptibly();

                stopFuture.complete(message);
              })
          .start();
    }
    return stopFuture;
  }

  public void stop() {
    try {
      stopAsync().get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.debug("Ignoring exception while stopping", e);
    }
  }

  public void close() {
    stop();
  }

  protected EventLoopGroup getEventLoopGroup() {
    return workerGroup;
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  public boolean isRunning() {
    return !bossGroup.isShuttingDown() || !workerGroup.isShuttingDown();
  }

  public List<Integer> getLocalPorts() {
    return getBoundPorts(serverChannelFutures);
  }

  /**
   * @deprecated use getLocalPort instead of getPort
   */
  @Deprecated
  public Integer getPort() {
    return getLocalPort();
  }

  public int getLocalPort() {
    return getFirstBoundPort(serverChannelFutures);
  }

  private Integer getFirstBoundPort(List<Future<Channel>> channelFutures) {
    for (Future<Channel> channelOpened : channelFutures) {
      try {
        return ((InetSocketAddress) channelOpened.get(15, SECONDS).localAddress()).getPort();
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        log.debug(
            "exception while retrieving port from channel future, ignoring port for this channel",
            e);
      }
    }
    return -1;
  }

  private List<Integer> getBoundPorts(List<Future<Channel>> channelFutures) {
    List<Integer> ports = new ArrayList<>();
    for (Future<Channel> channelOpened : channelFutures) {
      try {
        ports.add(((InetSocketAddress) channelOpened.get(3, SECONDS).localAddress()).getPort());
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        log.debug(
            "exception while retrieving port from channel future, ignoring port for this channel",
            e);
      }
    }
    return ports;
  }

  public List<Integer> bindServerPorts(final List<Integer> requestedPortBindings) {
    return bindPorts(serverServerBootstrap, requestedPortBindings, serverChannelFutures);
  }

  private List<Integer> bindPorts(
      final ServerBootstrap serverBootstrap,
      List<Integer> requestedPortBindings,
      List<Future<Channel>> channelFutures) {
    List<Integer> actualPortBindings = new ArrayList<>();
    for (final Integer portToBind : requestedPortBindings) {
      try {
        final CompletableFuture<Channel> channelOpened = new CompletableFuture<>();
        channelFutures.add(channelOpened);
        new Scheduler.SchedulerThreadFactory(
                getMockServerName() + " thread for port: " + portToBind, false)
            .newThread(
                () -> {
                  try {
                    InetSocketAddress inetSocketAddress = new InetSocketAddress(portToBind);
                    serverBootstrap
                        .bind(inetSocketAddress)
                        .addListener(
                            (ChannelFutureListener)
                                future -> {
                                  if (future.isSuccess()) {
                                    channelOpened.complete(future.channel());
                                  } else {
                                    channelOpened.completeExceptionally(future.cause());
                                  }
                                })
                        .channel()
                        .closeFuture()
                        .syncUninterruptibly();

                  } catch (Exception e) {
                    channelOpened.completeExceptionally(
                        new RuntimeException(
                            "Exception while binding MockServer to port " + portToBind, e));
                  }
                })
            .start();

        actualPortBindings.add(
            ((InetSocketAddress)
                    channelOpened
                        .get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS)
                        .localAddress())
                .getPort());

      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new RuntimeException(
            "Exception while binding MockServer to port " + portToBind,
            e instanceof ExecutionException ? e.getCause() : e);
      }
    }
    return actualPortBindings;
  }

  protected void startedServer(List<Integer> ports) {
    final String message =
        "started mockserver on port" + (ports.size() == 1 ? ": " + ports.get(0) : "s: " + ports);
    setPort(ports);
    log.trace(message);
  }
}
