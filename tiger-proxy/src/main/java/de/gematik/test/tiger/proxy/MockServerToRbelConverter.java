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
package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.test.tiger.mockserver.model.Header;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import de.gematik.test.tiger.mockserver.model.SocketAddress;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyParsingException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import de.gematik.test.tiger.proxy.exceptions.TigerRoutingErrorFacet;
import io.netty.channel.ChannelHandlerContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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
      HttpRequest request,
      HttpResponse response,
      String senderUrl,
      String receiverUrl,
      Optional<ZonedDateTime> timestamp,
      AtomicReference<String> previousMessageReference) {
    log.atTrace()
        .addArgument(response)
        .addArgument(response.getHeaders())
        .addArgument(() -> new String(response.getBody()))
        .log("Converting response {}, headers {}, body {}");

    final RbelElement responseRbelMessage = responseToRbelMessage(response, request);
    val conversionMetadata =
        new RbelMessageMetadata()
            .withSender(convertUri(senderUrl))
            .withReceiver(RbelHostname.fromString(receiverUrl).orElse(null))
            .withPreviousMessage(request.getCorrespondingRbelMessage().getUuid())
            .withPairedMessage(request.getCorrespondingRbelMessage().getUuid())
            .withTransmissionTime(timestamp.orElse(null))
            .withPreviousMessage(previousMessageReference.getAndSet(responseRbelMessage.getUuid()));
    return rbelConverter.parseMessageAsync(responseRbelMessage, conversionMetadata);
  }

  public CompletableFuture<RbelElement> convertRequest(
      HttpRequest request,
      String protocolAndHost,
      Optional<ZonedDateTime> timestamp,
      AtomicReference<String> previousMessageReference) {
    if (request.getCorrespondingRbelMessage() != null) {
      return CompletableFuture.completedFuture(request.getCorrespondingRbelMessage());
    }
    log.atTrace().addArgument(request::printLogLineDescription).log("Converting request {}");

    val unparsedRbelMessage = requestToRbelMessage(request);
    val conversionMetadata =
        new RbelMessageMetadata()
            .withSender(RbelHostname.fromString(request.getSenderAddress()).orElse(null))
            .withReceiver(convertUri(protocolAndHost))
            .withTransmissionTime(timestamp.orElse(null))
            .withPreviousMessage(previousMessageReference.getAndSet(unparsedRbelMessage.getUuid()));
    val parseMessageFuture =
        rbelConverter.parseMessageAsync(unparsedRbelMessage, conversionMetadata);

    request.setParsedMessageFuture(parseMessageFuture);
    request.setCorrespondingRbelMessage(unparsedRbelMessage);
    return parseMessageFuture;
  }

  public RbelElement convertErrorResponse(
      HttpRequest request,
      String protocolAndHost,
      TigerProxyRoutingException routingException,
      AtomicReference<String> previousMessageReference) {
    val message = new RbelElement(new byte[] {}, null);
    message.addFacet(new TigerRoutingErrorFacet(routingException));
    return rbelConverter.parseMessage(
        message,
        new RbelMessageMetadata()
            .withSender(convertUri(protocolAndHost))
            .withReceiver(
                Optional.ofNullable(request)
                    .map(HttpRequest::getReceiverAddress)
                    .map(SocketAddress::toRbelHostname)
                    .orElse(null))
            .withTransmissionTime(routingException.getTimestamp())
            .withPreviousMessage(previousMessageReference.getAndSet(message.getUuid())));
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

  public RbelElement responseToRbelMessage(final HttpResponse response, final HttpRequest request) {
    final byte[] httpMessage = responseToRawMessage(response);
    final RbelElement result = RbelElement.builder().rawContent(httpMessage).build();
    result.addFacet(new MockServerResponseFacet(request, response));
    return result;
  }

  public RbelElement requestToRbelMessage(final HttpRequest request) {
    final byte[] httpMessage = requestToRawMessage(request);
    final RbelElement result = RbelElement.builder().rawContent(httpMessage).build();
    result.addFacet(new MockServerRequestFacet(request));
    return result;
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

  public BiConsumer<TigerProxyRoutingException, ChannelHandlerContext> exceptionCallback() {
    return (exception, channelHandlerContext) -> {
      try {
        convertErrorResponse(null, null, exception, new AtomicReference<>(null));
      } catch (Exception e) {
        log.error("Error while converting error response", e);
      }
    };
  }
}
