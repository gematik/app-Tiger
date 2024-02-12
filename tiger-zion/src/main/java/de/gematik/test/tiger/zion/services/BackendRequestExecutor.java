/*
 * Copyright (c) 2024 gematik GmbH
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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import kong.unirest.Client;
import kong.unirest.Config;
import kong.unirest.Headers;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import kong.unirest.apache.ApacheClient;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
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
        var unirestResponse =
            prepareAndExecuteBackendRequest(requestDescription, requestRbelMessage);

        final RbelElement rbelResponse =
            rbelLogger
                .getRbelConverter()
                .convertElement(responseToRawMessage(unirestResponse), null);
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

  private HttpResponse<byte[]> prepareAndExecuteBackendRequest(
      ZionBackendRequestDescription requestDescription, RbelElement requestRbelMessage) {
    final String method = getMethod(requestDescription);
    try (UnirestInstance unirestInstance = createUnirestInstance()) {
      final HttpRequestWithBody unirestRequest =
          unirestInstance.request(
              method,
              TigerGlobalConfiguration.resolvePlaceholdersWithContext(
                  requestDescription.getUrl(),
                  new TigerJexlContext().withRootElement(requestRbelMessage)));

      if (requestDescription.getHeaders() != null) {
        requestDescription.getHeaders().forEach(unirestRequest::header);
      }
      final HttpResponse<byte[]> unirestResponse;
      if (StringUtils.isNotEmpty(requestDescription.getBody())) {
        final RbelSerializationResult body = getBody(requestDescription, requestRbelMessage);
        if (log.isTraceEnabled()) {
          log.trace(
              "About to sent {} with body {} to {}",
              unirestRequest.getHttpMethod().name(),
              body.getContentAsString(),
              unirestRequest.getUrl());
        }
        unirestResponse = unirestRequest.body(body.getContent()).asBytes();
      } else {
        if (log.isTraceEnabled()) {
          log.trace(
              "About to sent {} without body to {}",
              unirestRequest.getHttpMethod().name(),
              unirestRequest.getUrl());
        }
        unirestResponse = unirestRequest.asBytes();
      }
      return unirestResponse;
    }
  }

  private byte[] responseToRawMessage(HttpResponse<byte[]> response) {
    byte[] httpResponseHeader =
        ("HTTP/1.1 "
                + response.getStatus()
                + " "
                + (response.getStatusText() != null ? response.getStatusText() : "")
                + "\r\n"
                + formatHeaderList(response.getHeaders())
                + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);

    return ArrayUtils.addAll(httpResponseHeader, response.getBody());
  }

  private String formatHeaderList(Headers headerList) {
    return headerList.all().stream()
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

  private UnirestInstance createUnirestInstance() {
    UnirestInstance unirestInstance = Unirest.spawnInstance();

    unirestInstance.config().httpClient(buildHttpClient());
    return unirestInstance;
  }

  private Function<Config, Client> buildHttpClient() {
    var httpClientBuilder =
        HttpClients.custom()
            .setConnectionManager(
                new SpyOnSocketPortConnectionManager(zionConfiguration.getServerName()));
    if (zionConfiguration.getLocalTigerProxy() == null) {
      return ApacheClient.builder(httpClientBuilder.useSystemProperties().build());
    } else {
      return ApacheClient.builder(
          httpClientBuilder
              .setProxy(HttpHost.create(zionConfiguration.getLocalTigerProxy()))
              .build());
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
