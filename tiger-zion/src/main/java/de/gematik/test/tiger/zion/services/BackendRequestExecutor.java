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
package de.gematik.test.tiger.zion.services;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.GlobalServerMap;
import de.gematik.rbellogger.writer.RbelSerializationResult;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.zion.config.ZionBackendRequestDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.springframework.stereotype.Component;

/** Executes backend requests based on the provided descriptions. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackendRequestExecutor {

  private final ZionConfiguration zionConfiguration;
  private final RbelLogger rbelLogger;
  private final RbelWriter rbelWriter;

  public void executeBackendRequests(
      List<ZionBackendRequestDescription> backendRequests,
      TigerJexlContext jexlContext,
      RbelElement requestRbelMessage) {

    for (ZionBackendRequestDescription requestDescription : backendRequests) {
      try {
        var response = prepareAndExecuteBackendRequest(requestDescription, requestRbelMessage);

        final RbelElement rbelResponse =
            rbelLogger.getRbelConverter().convertElement(responseToRawMessage(response), null);
        VariableAssigner.doAssignments(
            requestDescription.getAssignments(), rbelResponse, jexlContext);
      } catch (RuntimeException e) {
        log.error(
            "Error during backend request '"
                + requestDescription.getMethod()
                + " "
                + requestDescription.getUrl()
                + "'",
            e);
        throw e;
      }
    }
  }

  @SneakyThrows
  private CloseableHttpResponse prepareAndExecuteBackendRequest(
      ZionBackendRequestDescription requestDescription, RbelElement requestRbelMessage) {
    final String method = getMethod(requestDescription);
    try (CloseableHttpClient apacheHttpClient = buildHttpClient()) {

      var httpRequest =
          RequestBuilder.create(method)
              .setUri(
                  TigerGlobalConfiguration.resolvePlaceholdersWithContext(
                      requestDescription.getUrl(),
                      new TigerJexlContext().withRootElement(requestRbelMessage)));

      if (requestDescription.getHeaders() != null) {
        requestDescription.getHeaders().forEach(httpRequest::addHeader);
      }
      CloseableHttpResponse response;
      if (StringUtils.isNotEmpty(requestDescription.getBody())) {
        final RbelSerializationResult body = getBody(requestDescription, requestRbelMessage);
        if (log.isTraceEnabled()) {
          log.trace(
              "About to sent {} with body {} to {}",
              httpRequest.getMethod(),
              body.getContentAsString(),
              httpRequest.getUri());
        }
        httpRequest.setEntity(new ByteArrayEntity(body.getContent()));
        response = apacheHttpClient.execute(httpRequest.build());
      } else {
        if (log.isTraceEnabled()) {
          log.trace(
              "About to sent {} without body to {}", httpRequest.getMethod(), httpRequest.getUri());
        }
        response = apacheHttpClient.execute(httpRequest.build());
      }
      return response;
    }
  }

  @SneakyThrows
  private byte[] responseToRawMessage(CloseableHttpResponse response) {
    byte[] httpResponseHeader =
        (response.getStatusLine()
                + "\r\n"
                + formatHeaderList(response.getAllHeaders())
                + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);

    return ArrayUtils.addAll(httpResponseHeader, response.getEntity().getContent().readAllBytes());
  }

  private String formatHeaderList(Header[] headerList) {
    return Arrays.stream(ArrayUtils.nullToEmpty(headerList))
        .map(Header.class::cast)
        .filter(
            h ->
                !h.getName()
                    .equalsIgnoreCase(
                        "Transfer-Encoding")) // chunks are already merged by the unirest client
        .map(h -> h.getName() + ": " + h.getValue())
        .collect(Collectors.joining("\r\n"));
  }

  private RbelSerializationResult getBody(
      ZionBackendRequestDescription requestDescription, RbelElement requestRbelMessage) {
    final String rawContent =
        TigerGlobalConfiguration.resolvePlaceholders(requestDescription.getBody());
    final RbelElement input = rbelLogger.getRbelConverter().convertElement(rawContent, null);
    return rbelWriter.serialize(input, new TigerJexlContext().withRootElement(requestRbelMessage));
  }

  private static String getMethod(ZionBackendRequestDescription requestDescription) {
    if (StringUtils.isEmpty(requestDescription.getMethod())) {
      if (StringUtils.isEmpty(requestDescription.getBody())) {
        return "GET";
      } else {
        return "POST";
      }
    }
    return TigerGlobalConfiguration.resolvePlaceholders(requestDescription.getMethod());
  }

  private CloseableHttpClient buildHttpClient() {
    var httpClientBuilder =
        HttpClients.custom()
            .setConnectionManager(
                new SpyOnSocketPortConnectionManager(zionConfiguration.getServerName()));
    if (zionConfiguration.getLocalTigerProxy() == null) {
      return httpClientBuilder.useSystemProperties().build();
    } else {
      return httpClientBuilder
          .setProxy(HttpHost.create(zionConfiguration.getLocalTigerProxy()))
          .build();
    }
  }

  @RequiredArgsConstructor
  private static class SpyOnSocketPortConnectionManager implements HttpClientConnectionManager {

    private final String serverName;

    @Delegate(types = HttpClientConnectionManager.class, excludes = DelegateExclusions.class)
    HttpClientConnectionManager delegate = new PoolingHttpClientConnectionManager();

    @Override
    public void connect(
        HttpClientConnection conn, HttpRoute route, int connectTimeout, HttpContext context)
        throws IOException {
      log.trace("Connecting to {}", route.getTargetHost());
      delegate.connect(conn, route, connectTimeout, context);
      int socketPort = ((ManagedHttpClientConnection) conn).getSocket().getLocalPort();
      log.trace("adding port {} and name {}", socketPort, serverName);
      GlobalServerMap.addServerNameForPort(socketPort, serverName);
    }

    interface DelegateExclusions {
      void connect(
          HttpClientConnection conn, HttpRoute route, int connectTimeout, HttpContext context);
    }
  }
}
