/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty;

import static de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration.configuration;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static de.gematik.test.tiger.mockserver.netty.HttpRequestHandler.PROXYING;
import static de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import de.gematik.test.tiger.mockserver.ExpectationBuilder;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.lifecycle.LifeCycle;
import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import de.gematik.test.tiger.proxy.data.TigerConnectionStatus;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Getter
@Slf4j
public class MockServer extends LifeCycle {

  private InetSocketAddress remoteSocket;
  private HttpActionHandler actionHandler;
  private Map<SocketAddress, TigerConnectionStatus> connectionStatusMap = new ConcurrentHashMap<>();

  /**
   * Start the instance using the ports provided
   *
   * @param localPorts the local port(s) to use, use 0 or no vararg values to specify any free port
   */
  public MockServer(final Integer... localPorts) {
    this(null, proxyConfiguration(configuration()), localPorts);
  }

  /**
   * Start the instance using the ports provided
   *
   * @param localPorts the local port(s) to use, use 0 or no vararg values to specify any free port
   */
  public MockServer(final MockServerConfiguration configuration, final Integer... localPorts) {
    this(configuration, proxyConfiguration(configuration), localPorts);
  }

  /**
   * Start the instance using the ports provided configuring forwarded or proxied requests to go via
   * an additional proxy
   *
   * @param proxyConfiguration the proxy configuration to send requests forwarded or proxied by
   *     MockServer via another proxy
   * @param localPorts the local port(s) to use, use 0 or no vararg values to specify any free port
   */
  public MockServer(final ProxyConfiguration proxyConfiguration, final Integer... localPorts) {
    this(null, List.of(proxyConfiguration), localPorts);
  }

  /**
   * Start the instance using the ports provided configuring forwarded or proxied requests to go via
   * an additional proxy
   *
   * @param proxyConfigurations the proxy configuration to send requests forwarded or proxied by
   *     MockServer via another proxy
   * @param localPorts the local port(s) to use, use 0 or no vararg values to specify any free port
   */
  public MockServer(
      final MockServerConfiguration configuration,
      final List<ProxyConfiguration> proxyConfigurations,
      final Integer... localPorts) {
    super(configuration);
    createServerBootstrap(configuration, proxyConfigurations, localPorts);

    // wait to start
    getLocalPort();
  }

  /**
   * Start the instance using the ports provided
   *
   * @param remotePort the port of the remote server to connect to
   * @param remoteHost the hostname of the remote server to connect to (if null defaults to
   *     "localhost")
   * @param localPorts the local port(s) to use
   */
  public MockServer(
      final Integer remotePort, @Nullable final String remoteHost, final Integer... localPorts) {
    this(null, proxyConfiguration(configuration()), remoteHost, remotePort, localPorts);
  }

  /**
   * Start the instance using the ports provided
   *
   * @param remotePort the port of the remote server to connect to
   * @param remoteHost the hostname of the remote server to connect to (if null defaults to
   *     "localhost")
   * @param localPorts the local port(s) to use
   */
  public MockServer(
      final MockServerConfiguration configuration,
      final Integer remotePort,
      @Nullable final String remoteHost,
      final Integer... localPorts) {
    this(configuration, proxyConfiguration(configuration), remoteHost, remotePort, localPorts);
  }

  /**
   * Start the instance using the ports provided configuring forwarded or proxied requests to go via
   * an additional proxy
   *
   * @param localPorts the local port(s) to use
   * @param remoteHost the hostname of the remote server to connect to (if null defaults to
   *     "localhost")
   * @param remotePort the port of the remote server to connect to
   */
  public MockServer(
      final MockServerConfiguration configuration,
      final ProxyConfiguration proxyConfiguration,
      @Nullable String remoteHost,
      final Integer remotePort,
      final Integer... localPorts) {
    this(configuration, List.of(proxyConfiguration), remoteHost, remotePort, localPorts);
  }

  /**
   * Start the instance using the ports provided configuring forwarded or proxied requests to go via
   * an additional proxy
   *
   * @param localPorts the local port(s) to use
   * @param remoteHost the hostname of the remote server to connect to (if null defaults to
   *     "localhost")
   * @param remotePort the port of the remote server to connect to
   */
  public MockServer(
      final MockServerConfiguration configuration,
      final List<ProxyConfiguration> proxyConfigurations,
      @Nullable String remoteHost,
      final Integer remotePort,
      final Integer... localPorts) {
    super(configuration);
    if (remotePort == null) {
      throw new IllegalArgumentException("You must specify a remote hostname");
    }
    if (isBlank(remoteHost)) {
      remoteHost = "localhost";
    }

    remoteSocket = new InetSocketAddress(remoteHost, remotePort);
    log.info("using proxy configuration for forwarded requests:{}", proxyConfigurations);
    createServerBootstrap(configuration, proxyConfigurations, localPorts);

    // wait to start
    getLocalPort();
  }

  private void createServerBootstrap(
      MockServerConfiguration configuration,
      final List<ProxyConfiguration> proxyConfigurations,
      final Integer... localPorts) {
    if (configuration == null) {
      configuration = configuration();
    }

    List<Integer> portBindings = singletonList(0);
    if (localPorts != null && localPorts.length > 0) {
      portBindings = Arrays.asList(localPorts);
    }

    final NettySslContextFactory nettyServerSslContextFactory =
        new NettySslContextFactory(configuration, true);
    final NettySslContextFactory nettyClientSslContextFactory =
        new NettySslContextFactory(configuration, false);

    actionHandler =
        new HttpActionHandler(
            configuration,
            getEventLoopGroup(),
            httpState,
            proxyConfigurations,
            nettyClientSslContextFactory);
    serverServerBootstrap =
        new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .channel(NioServerSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.DEBUG))
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(
                ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .childHandler(
                new MockServerChannelInitializer(
                    configuration, this, httpState, actionHandler, nettyServerSslContextFactory))
            .childAttr(REMOTE_SOCKET, remoteSocket)
            .childAttr(PROXYING, remoteSocket != null);

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

  public ExpectationBuilder when(HttpRequest httpRequest, Integer priority, List<String> hostRegexes) {
    return new ExpectationBuilder(new Expectation(httpRequest, priority, hostRegexes), this);
  }

  public ExpectationBuilder when(HttpRequest requestDefinition, List<String> hostRegexes) {
    return new ExpectationBuilder(new Expectation(requestDefinition, 0, hostRegexes), this);
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

  @RequiredArgsConstructor
  static class MockServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MockServerConfiguration configuration;
    private final MockServer mockServer;
    private final HttpState httpState;
    private final HttpActionHandler actionHandler;
    private final NettySslContextFactory nettyServerSslContextFactory;

    @Override
    public void initChannel(SocketChannel ch) {
      ch.pipeline().addFirst(new ConnectionCounterHandler(mockServer));

      ch.pipeline()
          .addLast(
              new MockServerUnificationInitializer(
                  configuration,
                  mockServer,
                  httpState,
                  actionHandler,
                  nettyServerSslContextFactory));
    }
  }
}
