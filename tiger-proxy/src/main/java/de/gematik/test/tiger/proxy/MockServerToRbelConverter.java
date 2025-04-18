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

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.mockserver.model.Header;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import de.gematik.test.tiger.mockserver.model.SocketAddress;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyParsingException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import de.gematik.test.tiger.proxy.exceptions.TigerRoutingErrorFacet;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.Arrays;

@RequiredArgsConstructor
@Slf4j
public class MockServerToRbelConverter {

  private final RbelConverter rbelConverter;

  public CompletableFuture<RbelElement> convertResponse(
      HttpResponse response,
      String senderUrl,
      String receiverUrl,
      CompletableFuture<RbelElement> pairedParsedRequest,
      Optional<ZonedDateTime> timestamp) {
    log.atTrace()
        .addArgument(response)
        .addArgument(response.getHeaders())
        .addArgument(() -> new String(response.getBody()))
        .log("Converting response {}, headers {}, body {}");

    return rbelConverter
        .parseMessageAsync(
            new RbelElementConvertionPair(responseToRbelMessage(response), pairedParsedRequest),
            convertUri(senderUrl),
            RbelHostname.fromString(receiverUrl).orElse(null),
            timestamp)
        .thenApply(element -> addHttpResponseFacetIfNotPresent(response, element));
  }

  private static RbelElement addHttpResponseFacetIfNotPresent(
      HttpResponse response, RbelElement element) {
    if (!element.hasFacet(RbelHttpResponseFacet.class)) {
      element.addFacet(
          RbelHttpResponseFacet.builder()
              .responseCode(
                  RbelElement.builder()
                      .parentNode(element)
                      .rawContent(response.getStatusCode().toString().getBytes())
                      .build())
              .build());
    }
    return element;
  }

  public CompletableFuture<RbelElement> convertRequest(
      HttpRequest request, String protocolAndHost, Optional<ZonedDateTime> timestamp) {
    if (request.getParsedRbelMessage() != null) {
      return CompletableFuture.completedFuture(request.getParsedRbelMessage());
    }
    log.atTrace().addArgument(request::printLogLineDescription).log("Converting request {}");

    return rbelConverter
        .parseMessageAsync(
            new RbelElementConvertionPair(requestToRbelMessage(request)),
            RbelHostname.fromString(request.getSenderAddress()).orElse(null),
            convertUri(protocolAndHost),
            timestamp)
        .thenApply(e -> addHttpRequestFacetIfNotPresent(request, e));
  }

  public RbelElement convertErrorResponse(
      HttpRequest request, String protocolAndHost, TigerProxyRoutingException routingException) {
    val message = new RbelElement(new byte[] {}, null);
    message.addFacet(new TigerRoutingErrorFacet(routingException));
    return rbelConverter.parseMessage(
        new RbelElementConvertionPair(message),
        convertUri(protocolAndHost),
        Optional.ofNullable(request)
            .map(HttpRequest::getReceiverAddress)
            .map(SocketAddress::toRbelHostname)
            .orElse(null),
        Optional.of(routingException.getTimestamp()));
  }

  private RbelElement addHttpRequestFacetIfNotPresent(HttpRequest request, RbelElement element) {
    if (!element.hasFacet(RbelHttpRequestFacet.class)) {
      element.addFacet(
          RbelHttpRequestFacet.builder()
              .path(RbelElement.wrap(element, request.getPath()))
              .method(RbelElement.wrap(element, request.getMethod()))
              .build());
    }

    return element;
  }

  private RbelHostname convertUri(String protocolAndHost) {
    if (protocolAndHost == null) {
      return null;
    }
    try {
      new URI(protocolAndHost);
      return (RbelHostname) RbelHostname.generateFromUrl(protocolAndHost).orElse(null);
    } catch (URISyntaxException e) {
      throw new TigerProxyParsingException(
          "Unable to parse hostname from '" + protocolAndHost + "'", e);
    }
  }

  public RbelElement responseToRbelMessage(final HttpResponse response) {
    final byte[] httpMessage = responseToRawMessage(response);
    return RbelElement.builder().rawContent(httpMessage).build();
  }

  public RbelElement requestToRbelMessage(final HttpRequest request) {
    final byte[] httpMessage = requestToRawMessage(request);
    return RbelElement.builder().rawContent(httpMessage).build();
  }

  private byte[] requestToRawMessage(HttpRequest request) {
    byte[] httpRequestHeader =
        (request.getMethod()
                + " "
                + getRequestUrl(request)
                + " HTTP/1.1\r\n"
                + formatHeaderList(request.getHeaderList())
                + "\r\n\r\n")
            .getBytes();

    return Arrays.concatenate(httpRequestHeader, request.getBody());
  }

  private byte[] responseToRawMessage(HttpResponse response) {
    byte[] httpResponseHeader =
        ("HTTP/1.1 "
                + response.getStatusCode()
                + " "
                + (response.getReasonPhrase() != null ? response.getReasonPhrase() : "")
                + "\r\n"
                + formatHeaderList(response.getHeaderList())
                + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);

    return Arrays.concatenate(httpResponseHeader, response.getBody());
  }

  private String formatHeaderList(List<Header> headerList) {
    return headerList.stream()
        .map(
            h ->
                h.getValues().stream()
                    .map(value -> h.getName() + ": " + value)
                    .collect(Collectors.joining("\r\n")))
        .collect(Collectors.joining("\r\n"));
  }

  private String getRequestUrl(HttpRequest request) {
    StringJoiner pathToQueryJoiner = new StringJoiner("?");
    if (StringUtils.isEmpty(request.getPath())) {
      pathToQueryJoiner.add("/");
    } else {
      pathToQueryJoiner.add(request.getPath());
    }

    if (request.getQueryStringParameters() != null
        && request.getQueryStringParameters().getEntries() != null) {
      pathToQueryJoiner.add(request.getQueryStringParameters().getRawParameterString());
    }

    return pathToQueryJoiner.toString();
  }
}
