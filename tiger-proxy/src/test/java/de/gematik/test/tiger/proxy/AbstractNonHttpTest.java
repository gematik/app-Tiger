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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.ByteArrayToStringRepresentation;
import de.gematik.test.tiger.common.data.config.tigerproxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.Data;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.core.ConditionTimeoutException;
import org.jetbrains.annotations.NotNull;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
@Data
public abstract class AbstractNonHttpTest {

  private TigerProxy tigerProxy;

  public static void writeSingleRequestMessage(Socket socket, byte[] message) throws IOException {
    socket.setSendBufferSize(message.length);
    OutputStream output = socket.getOutputStream();
    output.write(message);
    output.flush();
    log.info("msg written");
  }

  public static void readSingleResponseMessage(Socket socket, byte[] message) throws IOException {
    InputStream input = socket.getInputStream();
    final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    final String clientArrivedMessage = reader.readLine();
    log.info("msg read");
    assertThat((clientArrivedMessage + "\n").getBytes())
        .withRepresentation(new ByteArrayToStringRepresentation())
        .isEqualTo(message);
  }

  public void executeTestRunWithDirectReverseProxy(
      ThrowingConsumer<Socket> clientActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback,
      ThrowingConsumer<Socket> serverAcceptedConnectionCallback,
      RbelConverterPlugin... postConversionListeners)
      throws Exception {
    executeTestRunWithTls(
        clientActionCallback,
        interactionsVerificationCallback,
        serverAcceptedConnectionCallback,
        serverPort -> {
          var tigerProxy =
              new TigerProxy(
                  TigerProxyConfiguration.builder()
                      .directReverseProxy(
                          DirectReverseProxyInfo.builder()
                              .port(serverPort)
                              .hostname("localhost")
                              .build())
                      .build());
          if (postConversionListeners.length > 0) {
            ReflectionTestUtils.setField(
                tigerProxy,
                "rbelLogger",
                RbelLogger.build(
                    RbelConfiguration.builder()
                        .postConversionListener(List.of(postConversionListeners))
                        .build()));
          }
          return tigerProxy;
        });
  }

  public void executeTestRun(
      boolean withTls,
      ThrowingConsumer<Socket> clientActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback,
      ThrowingConsumer<Socket> serverAcceptedConnectionCallback,
      Function<Integer, TigerProxy> tigerProxyGenerator)
      throws Exception {
    try (GenericRespondingServer listenerServer = new GenericRespondingServer(withTls)) {
      AtomicInteger handlerCalledRequest = new AtomicInteger(0);
      AtomicInteger handlerCalledResponse = new AtomicInteger(0);
      AtomicInteger serverConnectionsOpenend = new AtomicInteger(0);
      listenerServer.setAcceptedConnectionConsumer(
          socket -> {
            serverConnectionsOpenend.incrementAndGet();
            serverAcceptedConnectionCallback.accept(socket);
          });
      tigerProxy = tigerProxyGenerator.apply(listenerServer.getLocalPort());

      final MockServerConfiguration configuration =
          (MockServerConfiguration)
              ReflectionTestUtils.getField(
                  ReflectionTestUtils.getField(tigerProxy, "mockServer"), "configuration");
      val oldListener = configuration.binaryProxyListener();
      configuration.binaryProxyListener(
          new BinaryExchangeHandler(tigerProxy) {
            @Override
            public void onProxy(
                BinaryMessage binaryMessage,
                SocketAddress serverAddress,
                SocketAddress clientAddress) {
              log.info(
                  "ports are {} and {}",
                  ((InetSocketAddress) serverAddress).getPort(),
                  ((InetSocketAddress) clientAddress).getPort());
              // with new direct connection to remote server, there is no longer
              // pairing of request/response, so we identify it based on the direction the message
              // is going
              if (((InetSocketAddress) serverAddress).getPort() == listenerServer.getLocalPort()) {
                handlerCalledRequest.incrementAndGet();
              } else {
                handlerCalledResponse.incrementAndGet();
              }
              oldListener.onProxy(binaryMessage, serverAddress, clientAddress);
            }
          });

      CompletableFuture<Void> executionResult =
          CompletableFuture.supplyAsync(
              () -> {
                try (Socket clientSocket = newClientSocketTo(tigerProxy, withTls)) {
                  log.info("listenerServer on port: " + listenerServer.getLocalPort());
                  clientActionCallback.accept(clientSocket);
                } catch (IOException e) {
                  log.error("Exception while accepting client socket", e);
                  throw new RuntimeException(e);
                } catch (ConditionTimeoutException cte) {
                  tigerProxy
                      .getRbelMessagesList()
                      .forEach(msg -> log.error(msg.printHttpDescription()));
                  throw cte;
                }
                return null;
              });

      try {
        await()
            .atMost(10, TimeUnit.SECONDS)
            .failFast(executionResult::isCompletedExceptionally)
            // response
            .ignoreExceptions()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .untilAsserted(
                () -> {
                  log.info(
                      "Verifying interactions... (requests="
                          + handlerCalledRequest.get()
                          + ", response="
                          + handlerCalledResponse.get()
                          + ", serverCalled="
                          + serverConnectionsOpenend.get()
                          + ", rbelMsgs="
                          + getTigerProxy().getRbelMessages().size()
                          + ")");
                  interactionsVerificationCallback.acceptThrows(
                      handlerCalledRequest, handlerCalledResponse, serverConnectionsOpenend);
                });
      } catch (RuntimeException e) {
        log.error("Exception while executing test", e);
        log.error(
            "Found messages: \n\n{}",
            getTigerProxy().getRbelMessages().stream()
                .map(RbelElement::printTreeStructureWithoutColors)
                .collect(Collectors.joining("\n\n\n\n")));
        throw e;
      }
    } finally {
      if (tigerProxy != null) {
        tigerProxy.close();
      }
    }
  }

  public void executeTestRunWithTls(
      ThrowingConsumer<Socket> clientActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback,
      ThrowingConsumer<Socket> serverAcceptedConnectionCallback,
      Function<Integer, TigerProxy> tigerProxyGenerator)
      throws Exception {
    executeTestRun(
        true,
        clientActionCallback,
        interactionsVerificationCallback,
        serverAcceptedConnectionCallback,
        tigerProxyGenerator);
  }

  public static class GenericRespondingServer implements AutoCloseable {

    private final ServerSocket listenerServer;
    @Setter private ThrowingConsumer<Socket> acceptedConnectionConsumer;
    private final Thread serverThread;
    private boolean shouldRun = true;
    private final AtomicReference<Throwable> serverSidedException = new AtomicReference<>();

    public GenericRespondingServer(boolean secure) {
      listenerServer = newServerSocket(secure);
      final AtomicBoolean isServerReady = new AtomicBoolean(false);
      serverThread =
          new Thread(
              () -> {
                isServerReady.set(true);
                log.info(
                    "listener server: waiting for incoming connections on port {} & {} & {}...",
                    listenerServer.getLocalPort(),
                    listenerServer.getInetAddress(),
                    listenerServer.getLocalSocketAddress());
                while (shouldRun) {
                  try {
                    final Socket serverSocket = listenerServer.accept();
                    Executors.newCachedThreadPool()
                        .execute(
                            () -> {
                              log.info(
                                  "listener server: got connection from port {}",
                                  serverSocket.getPort());
                              acceptedConnectionConsumer.accept(serverSocket);
                            });
                    log.info("listener server: after assert");
                  } catch (IOException e) {
                    // swallow socket close exceptions. makes for a less confusing test run output
                    if (!(e instanceof SocketException)) {
                      log.error("IGNORED!", e);
                      serverSidedException.set(e);
                    }
                  } catch (Throwable e) {
                    serverSidedException.set(e);
                  }
                }
              },
              "Asynch-server-thread");
      serverThread.start();
      await().until(isServerReady::get);
    }

    @Override
    public void close() throws Exception {
      shouldRun = false;
      log.info("Closing server...");
      listenerServer.close();
      log.info("Joining server thread...");
      serverThread.join(1000);
      log.info("Shutdown complete! ({})", serverSidedException.get());
      if (serverSidedException.get() != null) {
        throw new RuntimeException("Server threw exception", serverSidedException.get());
      }
    }

    public Integer getLocalPort() {
      return listenerServer.getLocalPort();
    }
  }

  @FunctionalInterface
  public interface ThrowingConsumer<T> extends Consumer<T> {

    @Override
    default void accept(final T elem) {
      try {
        acceptThrows(elem);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    void acceptThrows(T elem) throws Exception;
  }

  public interface VerifyInteractionsConsumer {

    void acceptThrows(
        AtomicInteger request, AtomicInteger responses, AtomicInteger serverConnectionsOpened)
        throws Exception;
  }

  @NotNull
  public static Socket newClientSocketTo(TigerProxy tigerProxy, boolean secure) throws IOException {
    if (secure) {
      return tigerProxy
          .buildSslContext()
          .getSocketFactory()
          .createSocket("localhost", tigerProxy.getProxyPort());
    } else {
      return new Socket("localhost", tigerProxy.getProxyPort());
    }
  }

  @NotNull
  @SneakyThrows
  public static ServerSocket newServerSocket(boolean secure) {
    if (secure) {
      final KeyStore ks = buildTruststore();
      final TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ks);
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(ks, "gematik".toCharArray());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

      return sslContext.getServerSocketFactory().createServerSocket(0);
    } else {
      return new ServerSocket(0);
    }
  }

  @SneakyThrows
  private static KeyStore buildTruststore() {
    final TigerPkiIdentity serverIdentity =
        new TigerPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");
    return serverIdentity.toKeyStoreWithPassword("gematik");
  }
}
