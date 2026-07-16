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
package de.gematik.test.tiger.proxy.tls;

import de.gematik.test.tiger.common.data.config.tigerproxy.AlpnProtocol;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Probes a TLS backend to discover its ALPN (Application-Layer Protocol Negotiation) capabilities.
 */
@Slf4j
public class BackendAlpnProber {

  public static final String HTTP_1_1 = AlpnProtocol.HTTP_1_1.getValue();
  public static final String HTTP_2 = AlpnProtocol.H2.getValue();
  private static final String[] ALPN_PROBE_PROTOCOLS = {HTTP_2, HTTP_1_1};
  private static final int PROBE_TIMEOUT_MS = 3_000;

  private BackendAlpnProber() {
    // utility class
  }

  public static Optional<AlpnProtocol> probe(String host, int port) {
    return probe(host, port, null);
  }

  public static Optional<AlpnProtocol> probe(URL url) {
    return probe(url, null);
  }

  public static Optional<AlpnProtocol> probe(URL url, ProxyConfiguration forwardProxy) {
    if (!"https".equalsIgnoreCase(url.getProtocol())) {
      return Optional.empty();
    }
    int port = url.getPort();
    if (port < 0) {
      port = url.getDefaultPort();
    }
    if (port < 0) {
      log.debug("Cannot probe ALPN for {}: no port and no default for protocol", url);
      return Optional.empty();
    }
    return probe(url.getHost(), port, forwardProxy);
  }

  public static Optional<AlpnProtocol> probe(
      String host, int port, ProxyConfiguration forwardProxy) {
    if (forwardProxy != null
        && shouldUseProxy(host, forwardProxy)
        && forwardProxy.getType() == ProxyConfiguration.Type.SOCKS5) {
      log.debug(
          "Skipping ALPN probe to {}:{}: SOCKS5 forward proxies are not supported by the prober",
          host,
          port);
      return Optional.empty();
    }
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {INSECURE_TRUST_MANAGER}, new SecureRandom());

      return doAlpnProbe(host, port, forwardProxy, sslContext);
    } catch (IOException | GeneralSecurityException | RuntimeException e) {
      log.debug("ALPN probe to {}:{} failed: {}", host, port, e.getMessage(), e);
      return Optional.empty();
    }
  }

  private static Optional<AlpnProtocol> doAlpnProbe(
      String host, int port, ProxyConfiguration forwardProxy, SSLContext sslContext)
      throws IOException, GeneralSecurityException {
    try (Socket rawSocket = openTransportSocket(host, port, forwardProxy);
        SSLSocket socket =
            (SSLSocket) sslContext.getSocketFactory().createSocket(rawSocket, host, port, true)) {
      socket.setSoTimeout(PROBE_TIMEOUT_MS);

      SSLParameters params = socket.getSSLParameters();
      params.setApplicationProtocols(ALPN_PROBE_PROTOCOLS);
      socket.setSSLParameters(params);

      socket.startHandshake();

      String negotiated = socket.getApplicationProtocol();
      log.trace("ALPN probe to {}:{} negotiated: {}", host, port, negotiated);

      if (negotiated != null && !negotiated.isEmpty()) {
        return getAlpnProtocol(host, port, negotiated);
      }
      return Optional.empty();
    }
  }

  static Optional<AlpnProtocol> getAlpnProtocol(String host, int port, String negotiated) {
    try {
      return Optional.of(AlpnProtocol.fromValue(negotiated));
    } catch (IllegalArgumentException e) {
      log.debug(
          "ALPN probe to {}:{} negotiated unrecognised protocol '{}', ignoring",
          host,
          port,
          negotiated,
          e);
      return Optional.empty();
    }
  }

  public static List<AlpnProtocol> deriveServerAlpnProtocols(
      Optional<AlpnProtocol> probedProtocol) {
    if (probedProtocol.isEmpty()) {
      return List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1);
    }
    if (probedProtocol.get() == AlpnProtocol.H2) {
      return List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1);
    } else {
      return List.of(AlpnProtocol.HTTP_1_1);
    }
  }

  private static Socket openTransportSocket(String host, int port, ProxyConfiguration forwardProxy)
      throws IOException, GeneralSecurityException {
    if (forwardProxy == null || !shouldUseProxy(host, forwardProxy)) {
      Socket s = new Socket();
      try {
        s.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
        return s;
      } catch (IOException | RuntimeException e) {
        closeQuietly(s);
        throw e;
      }
    }
    return openTunnelThroughProxy(host, port, forwardProxy);
  }

  private static Socket openTunnelThroughProxy(
      String host, int port, ProxyConfiguration forwardProxy)
      throws IOException, GeneralSecurityException {
    InetSocketAddress proxyAddress = forwardProxy.getProxyAddress();
    Socket transport = new Socket();
    try {
      transport.connect(proxyAddress, PROBE_TIMEOUT_MS);
      transport.setSoTimeout(PROBE_TIMEOUT_MS);
      if (forwardProxy.getType() == ProxyConfiguration.Type.HTTPS) {
        transport = wrapWithTls(transport, proxyAddress);
      }
      sendConnect(transport, host, port, forwardProxy);
      return transport;
    } catch (IOException | GeneralSecurityException | RuntimeException e) {
      closeQuietly(transport);
      throw e;
    }
  }

  /**
   * Wraps {@code raw} in a TLS layer. The caller transfers ownership: on success the returned
   * {@link SSLSocket} owns {@code raw} (autoClose); on failure {@code raw} is closed before the
   * exception propagates.
   *
   * <p>Package-private for direct testing.
   */
  static SSLSocket wrapWithTls(Socket raw, InetSocketAddress proxyAddress)
      throws IOException, GeneralSecurityException {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, new TrustManager[] {INSECURE_TRUST_MANAGER}, new SecureRandom());
    SSLSocket tls = null;
    try {
      tls =
          (SSLSocket)
              sslContext
                  .getSocketFactory()
                  .createSocket(raw, proxyAddress.getHostString(), proxyAddress.getPort(), true);
      tls.setSoTimeout(PROBE_TIMEOUT_MS);
      tls.startHandshake();
      return tls;
    } catch (IOException | RuntimeException e) {
      if (tls != null) {
        closeQuietly(tls); // autoClose=true closes raw too
      } else {
        closeQuietly(raw);
      }
      throw e;
    }
  }

  private static void closeQuietly(Socket socket) {
    if (socket == null) {
      return;
    }
    try {
      socket.close();
    } catch (IOException ignored) {
      // best-effort cleanup
    }
  }

  private static void sendConnect(
      Socket transport, String host, int port, ProxyConfiguration forwardProxy) throws IOException {
    String connectTarget = host + ":" + port;
    StringBuilder request = new StringBuilder();
    request
        .append("CONNECT ")
        .append(connectTarget)
        .append(" HTTP/1.1\r\n")
        .append("Host: ")
        .append(connectTarget)
        .append("\r\n")
        .append("Proxy-Connection: keep-alive\r\n");
    String authHeader = basicAuthHeader(forwardProxy);
    if (authHeader != null) {
      request.append("Proxy-Authorization: ").append(authHeader).append("\r\n");
    }
    request.append("\r\n");

    OutputStream out = transport.getOutputStream();
    out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
    out.flush();

    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(transport.getInputStream(), StandardCharsets.US_ASCII));
    String statusLine = reader.readLine();
    if (statusLine == null) {
      throw new IOException("Forward proxy closed the connection before responding to CONNECT");
    }
    String[] parts = statusLine.split(" ", 3);
    if (parts.length < 2 || !"200".equals(parts[1])) {
      throw new IOException("CONNECT to " + connectTarget + " via proxy failed: " + statusLine);
    }
    String line;
    do {
      line = reader.readLine();
    } while (line != null && !line.isEmpty());
  }

  private static String basicAuthHeader(ProxyConfiguration forwardProxy) {
    String user = forwardProxy.getUsername();
    String pass = forwardProxy.getPassword();
    if (user == null || user.isEmpty() || pass == null) {
      return null;
    }
    String token =
        Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
  }

  /** Returns false if {@code host} matches one of the proxy's no-proxy entries. */
  private static boolean shouldUseProxy(String host, ProxyConfiguration forwardProxy) {
    List<String> noProxyHosts = forwardProxy.getNoProxyHosts();
    if (noProxyHosts == null || noProxyHosts.isEmpty() || host == null) {
      return true;
    }
    String normalized = host.toLowerCase(Locale.ROOT);
    for (String entry : noProxyHosts) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      String e = entry.trim().toLowerCase(Locale.ROOT);
      if (normalized.equals(e) || normalized.endsWith("." + e)) {
        return false;
      }
    }
    return true;
  }

  private static final X509TrustManager INSECURE_TRUST_MANAGER =
      new X509TrustManager() {
        @Override
        @SuppressWarnings("java:S4830")
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
          // trust all
        }

        @Override
        @SuppressWarnings("java:S4830")
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
          // trust all
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };
}
