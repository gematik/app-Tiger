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
package de.gematik.test.tiger.mockserver.netty;

import static de.gematik.rbellogger.util.MemoryConstants.KB;
import static de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration.configuration;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static java.util.Collections.singletonList;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.lifecycle.LifeCycle;
import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import de.gematik.test.tiger.proxy.data.TigerConnectionStatus;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.SocketUtils;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/*
 * @author jamesdbloom
 */
@Getter
@Slf4j
public class MockServer extends LifeCycle {

  private final InetSocketAddress remoteSocket;
  private HttpActionHandler actionHandler;
  private final Map<SocketAddress, TigerConnectionStatus> connectionStatusMap =
      new ConcurrentHashMap<>();
  private NettySslContextFactory serverSslContextFactory;
  private NettySslContextFactory clientSslContextFactory;
  private MockServerInfiniteLoopChecker infiniteLoopChecker;

  /**
   * Start the instance using the ports provided
   *
   * @param localPorts the local port(s) to use, use 0 or no vararg values to specify any free port
   */
  public MockServer(final MockServerConfiguration configuration, final Integer... localPorts) {
    super(configuration);
    if (configuration.directForwarding() != null) {
      remoteSocket =
          SocketUtils.socketAddress(
              configuration.directForwarding().getHostName(),
              configuration.directForwarding().getPort());
    } else {
      remoteSocket = null;
    }
    createServerBootstrap(configuration, localPorts);

    // wait to start
    getLocalPort();
  }

  private void createServerBootstrap(
      MockServerConfiguration configuration, final Integer... localPorts) {
    if (configuration == null) {
      configuration = configuration();
    }

    List<Integer> portBindings = singletonList(0);
    if (localPorts != null && localPorts.length > 0) {
      portBindings = Arrays.asList(localPorts);
    }

    serverSslContextFactory = new NettySslContextFactory(configuration, true);
    clientSslContextFactory = new NettySslContextFactory(configuration, false);
    val httpClient =
        new NettyHttpClient(configuration, getEventLoopGroup(), clientSslContextFactory);
    infiniteLoopChecker = new MockServerInfiniteLoopChecker(httpClient);

    actionHandler = new HttpActionHandler(configuration, httpState, httpClient);
    serverServerBootstrap =
        new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .option(ChannelOption.SO_BACKLOG, 1 * KB)
            .channel(NioServerSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.DEBUG))
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(
                ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .childHandler(
                new MockServerChannelInitializer(configuration, this, httpState, actionHandler))
            .childAttr(REMOTE_SOCKET, remoteSocket);

    try {
      bindServerPorts(portBindings);
    } catch (RuntimeException throwable) {
      log.error("exception binding to port(s) {}", portBindings, throwable);
      stop();
      throw throwable;
    }
    startedServer(getLocalPorts());
  }

  public InetSocketAddress getRemoteAddress() {
    return remoteSocket;
  }

  public void removeExpectation(String expectationId) {
    httpState.clear(expectationId);
  }

  public List<Expectation> retrieveActiveExpectations() {
    return httpState.retrieveActiveExpectations();
  }

  public synchronized void addConnectionWithStatus(
      SocketAddress socketAddress, TigerConnectionStatus status) {
    connectionStatusMap.put(socketAddress, status);
  }

  public synchronized Map<SocketAddress, TigerConnectionStatus> getOpenConnections() {
    return connectionStatusMap.entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  public synchronized void removeRemoteAddress(SocketAddress socketAddress) {
    connectionStatusMap.remove(socketAddress);
  }

  public Expectation addRoute(Expectation expectation) {
    getHttpState().add(expectation);
    return expectation;
  }

  public void waitForAllParsingTasksToBeFinished() {
    Optional.of(getConfiguration())
        .map(MockServerConfiguration::binaryProxyListener)
        .ifPresent(BinaryExchangeHandler::waitForAllParsingTasksToBeFinished);
  }

  @RequiredArgsConstructor
  class MockServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MockServerConfiguration configuration;
    private final MockServer mockServer;
    private final HttpState httpState;
    private final HttpActionHandler actionHandler;

    @Override
    public void initChannel(SocketChannel ch) {
      ch.pipeline().addFirst(new LoggingHandler(LogLevel.DEBUG));
      ch.pipeline().addLast(new ConnectionCounterHandler(mockServer));

      ch.pipeline()
          .addLast(
              new PortUnificationHandler(
                  configuration, mockServer, httpState, actionHandler, infiniteLoopChecker));
    }
  }
}
