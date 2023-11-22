/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerProxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.BinaryProxyListener;
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
    log("msg written");
  }

  public static void readSingleResponseMessage(Socket socket, byte[] message) throws IOException {
    InputStream input = socket.getInputStream();
    final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    final String clientArrivedMessage = reader.readLine();
    assertThat((clientArrivedMessage + "\n").getBytes()).isEqualTo(message);
  }

  private static void log(String s) {
    log.info(s);
  }

  public void executeTestRun(
      ThrowingConsumer<Socket> clientActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback,
      ThrowingConsumer<Socket> serverAcceptedConnectionCallback)
      throws Exception {
    executeTestRun(
        clientActionCallback,
        interactionsVerificationCallback,
        serverAcceptedConnectionCallback,
        serverPort ->
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .directReverseProxy(
                        DirectReverseProxyInfo.builder()
                            .port(serverPort)
                            .hostname("localhost")
                            .build())
                    .build()));
  }

  public void executeTestRun(
      ThrowingConsumer<Socket> clientActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback,
      ThrowingConsumer<Socket> serverAcceptedConnectionCallback,
      Function<Integer, TigerProxy> tigerProxyGenerator)
      throws Exception {
    try (GenericRespondingServer listenerServer = new GenericRespondingServer()) {
      AtomicInteger handlerCalledRequest = new AtomicInteger(0);
      AtomicInteger handlerCalledResponse = new AtomicInteger(0);
      AtomicInteger serverCalled = new AtomicInteger(0);
      listenerServer.setAcceptedConnectionConsumer(
          socket -> {
            serverCalled.incrementAndGet();
            serverAcceptedConnectionCallback.accept(socket);
          });
      tigerProxy = tigerProxyGenerator.apply(listenerServer.getLocalPort());

      final Configuration configuration =
          (Configuration)
              ReflectionTestUtils.getField(
                  ReflectionTestUtils.getField(tigerProxy, "mockServer"), "configuration");
      final BinaryProxyListener oldListener = configuration.binaryProxyListener();
      configuration.binaryProxyListener(
          (binaryMessage, completableFuture, socketAddress, socketAddress1) -> {
            log.info(
                "ports are {} and {}",
                ((InetSocketAddress) socketAddress).getPort(),
                ((InetSocketAddress) socketAddress1).getPort());
            handlerCalledRequest.incrementAndGet();
            log.info(
                "call received to the binary handler. req is '{}'",
                StringUtils.abbreviate(new String(binaryMessage.getBytes()), 100));
            completableFuture.thenApply(
                msg -> {
                  if (msg != null) {
                    handlerCalledResponse.incrementAndGet();
                    log.info(
                        "call received to the binary handler. resp is '{}'",
                        StringUtils.abbreviate(new String(msg.getBytes()), 100));
                  } else {
                    log.info("call received to the binary handler. resp is null");
                  }
                  return msg;
                });
            oldListener.onProxy(binaryMessage, completableFuture, socketAddress, socketAddress1);
          });
      try (Socket clientSocket = newClientSocketTo(tigerProxy)) {
        log("listenerServer on port: " + listenerServer.getLocalPort());
        clientActionCallback.accept(clientSocket);
      } catch (IOException e) {
        log.error("Exception while accepting client socket", e);
        throw new RuntimeException(e);
      }

      try {
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollDelay(
                1000,
                TimeUnit.MILLISECONDS) // to ensure the server would have had a chance to handle a
            // response
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .untilAsserted(
                () -> {
                  log(
                      "Verifying interactions... (requests="
                          + handlerCalledRequest.get()
                          + ", response="
                          + handlerCalledResponse.get()
                          + ", serverCalled="
                          + serverCalled.get()
                          + ", rbelMsgs="
                          + getTigerProxy().getRbelMessages().size()
                          + ")");
                  interactionsVerificationCallback.acceptThrows(
                      handlerCalledRequest, handlerCalledResponse, serverCalled);
                });
      } catch (RuntimeException e) {
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

  public static class GenericRespondingServer implements AutoCloseable {

    private final ServerSocket listenerServer;
    private ThrowingConsumer<Socket> acceptedConnectionConsumer;

    public GenericRespondingServer() {
      listenerServer = newSslServerSocket();
      AtomicBoolean isServerReady = new AtomicBoolean(false);
      new Thread(
              () -> {
                isServerReady.set(true);
                log.info(
                    "listener server: waiting for incoming connections on port {} & {} & {}...",
                    listenerServer.getLocalPort(),
                    listenerServer.getInetAddress(),
                    listenerServer.getLocalSocketAddress());
                while (true) {
                  try {
                    final Socket serverSocket = listenerServer.accept();
                    log.info(
                        "listener server: got connection from port {}", serverSocket.getPort());
                    acceptedConnectionConsumer.accept(serverSocket);
                    log("listener server: after assert");
                  } catch (IOException e) {
                    // swallow socket close exceptions. makes for a less confusing test run output
                    if (!(e instanceof SocketException)) {
                      log.error("IGNORED!", e);
                    }
                  }
                }
              })
          .start();
      await().until(isServerReady::get);
    }

    @Override
    public void close() throws Exception {
      listenerServer.close();
    }

    public void setAcceptedConnectionConsumer(ThrowingConsumer<Socket> acceptedConnectionConsumer) {
      this.acceptedConnectionConsumer = acceptedConnectionConsumer;
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

  @FunctionalInterface
  public interface ThrowingSupplier<T> extends Supplier<T> {

    @Override
    default T get() {
      try {
        return getThrows();
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    T getThrows() throws Exception;
  }

  public interface VerifyInteractionsConsumer {

    void acceptThrows(AtomicInteger request, AtomicInteger responses, AtomicInteger serverCalls)
        throws Exception;
  }

  @NotNull
  public static Socket newClientSocketTo(TigerProxy tigerProxy) throws IOException {
    return tigerProxy
        .buildSslContext()
        .getSocketFactory()
        .createSocket("localhost", tigerProxy.getProxyPort());
  }

  @NotNull
  @SneakyThrows
  public static ServerSocket newSslServerSocket() {
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
  }

  @SneakyThrows
  private static KeyStore buildTruststore() {
    final TigerPkiIdentity serverIdentity =
        new TigerPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");
    return serverIdentity.toKeyStoreWithPassword("gematik");
  }
}
