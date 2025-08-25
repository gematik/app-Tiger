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
package de.gematik.test.tiger.proxy.handler;

import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.facets.uri.RbelUriFacet;
import de.gematik.rbellogger.facets.uri.RbelUriParameterFacet;
import de.gematik.rbellogger.util.GlobalServerMap;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationCallback;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyModificationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyParsingException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Abstract super type handling the parsing logic for messages. It is the essential hook which
 * allows the TigerProxy to gather the messages going through the MockServer. The actual
 * implementations of this class deal with the routing of the messages.
 */
@Data
@Slf4j
public abstract class AbstractTigerRouteCallback implements ExpectationCallback {

  public static final String LOCATION_HEADER_KEY = "Location";
  private final TigerProxy tigerProxy;
  private final TigerProxyRoute tigerRoute;
  private AtomicReference<String> previousMessageUuid = new AtomicReference<>(null);

  protected AbstractTigerRouteCallback(TigerProxy tigerProxy, TigerProxyRoute tigerRoute) {
    this.tigerProxy = tigerProxy;
    this.tigerRoute = tigerRoute;
  }

  public void applyModifications(HttpRequest request) {
    if (!tigerProxy.getModifications().isEmpty()) {
      parseMessageAndApplyModifications(request);
    }
  }

  public void parseMessageAndApplyModifications(HttpRequest request) {
    final RbelElement requestElement =
        tigerProxy
            .getRbelLogger()
            .getRbelConverter()
            .convertElement(
                tigerProxy.getMockServerToRbelConverter().requestToRbelMessage(request));
    final RbelElement modifiedRequest =
        tigerProxy.getRbelLogger().getRbelModifier().applyModifications(requestElement);
    if (modifiedRequest == requestElement) {
      return;
    }
    request.withBody(extractSafe(modifiedRequest, "$.body").getRawContent());
    for (RbelElement modifiedHeader : modifiedRequest.findRbelPathMembers("$.header.*")) {
      request =
          request.replaceHeader(
              header(modifiedHeader.getKey().orElseThrow(), modifiedHeader.getRawStringContent()));
    }
    final RbelUriFacet uriFacet =
        extractSafe(modifiedRequest, "$.path").getFacetOrFail(RbelUriFacet.class);
    request.setPath(uriFacet.getBasicPathString());
    clearExistingQueryParameters(request);
    addAllQueryParametersFromRbelMessage(request, uriFacet);
    request.setMethod(extractSafe(modifiedRequest, "$.method").getRawStringContent());
  }

  private RbelElement extractSafe(RbelElement targetElement, String rbelPath) {
    return targetElement
        .findElement(rbelPath)
        .orElseThrow(
            () ->
                new TigerProxyModificationException(
                    "Unexpected structure: Could not find '" + rbelPath + "'!"));
  }

  private void addAllQueryParametersFromRbelMessage(HttpRequest request, RbelUriFacet uriFacet) {
    for (RbelElement queryElement : uriFacet.getQueryParameters()) {
      final RbelUriParameterFacet parameterFacet =
          queryElement.getFacetOrFail(RbelUriParameterFacet.class);
      request.withQueryStringParameter(
          parameterFacet.getKeyAsString(), parameterFacet.getValue().getRawStringContent());
    }
  }

  private void clearExistingQueryParameters(HttpRequest request) {
    final Parameters queryStringParameters = request.getQueryStringParameters();
    if (queryStringParameters == null) {
      return;
    }
    queryStringParameters
        .getEntries()
        .forEach(parameter -> queryStringParameters.remove(parameter.getName()));
  }

  public void applyModifications(HttpRequest request, HttpResponse response) {
    if (!tigerProxy.getModifications().isEmpty()) {
      parseMessageAndApplyModifications(request, response);
    }
  }

  public void parseMessageAndApplyModifications(HttpRequest request, HttpResponse response) {
    final RbelElement responseElement =
        tigerProxy
            .getRbelLogger()
            .getRbelConverter()
            .convertElement(
                tigerProxy.getMockServerToRbelConverter().responseToRbelMessage(response, request));
    final RbelElement modifiedResponse =
        tigerProxy.getRbelLogger().getRbelModifier().applyModifications(responseElement);
    if (modifiedResponse != responseElement) {
      modifyOriginalResponse(response, modifiedResponse);
    }
  }

  private void modifyOriginalResponse(HttpResponse response, RbelElement modifiedResponse) {
    response.withBody(extractSafe(modifiedResponse, "$.body").getRawContent());
    response.getHeaderMultimap().clear();
    for (RbelElement modifiedHeader : modifiedResponse.findRbelPathMembers("$.header.*")) {
      response =
          response.withHeader(
              header(modifiedHeader.getKey().orElseThrow(), modifiedHeader.getRawStringContent()));
    }
    response.withStatusCode(
        Integer.parseInt(extractSafe(modifiedResponse, "$.responseCode").getRawStringContent()));
    final String reasonPhrase =
        extractSafe(modifiedResponse, "$.reasonPhrase").getRawStringContent();
    if (!StringUtils.isEmpty(reasonPhrase)) {
      response.withReasonPhrase(reasonPhrase);
    } else {
      response.withReasonPhrase(" ");
    }
  }

  @Override
  public final HttpRequest handle(HttpRequest req) {
    try {
      doIncomingRequestLogging(req);
      final HttpRequest modifiedRequest = handleRequest(req);
      if (shouldLogTraffic()) {
        parseMessage(req);
      }
      return modifiedRequest;
    } catch (RuntimeException e) {
      log.warn("Uncaught exception during handling of request", e);
      propagateExceptionMessageSafe(e);
      throw e;
    }
  }

  public void propagateExceptionMessageSafe(Exception exception) {
    try {
      tigerProxy.propagateException(exception);
    } catch (Exception handlingException) {
      log.warn(
          "While propagating an exception another error occurred (ignoring):", handlingException);
    }
  }

  protected abstract HttpRequest handleRequest(HttpRequest req);

  @Override
  public final HttpResponse handle(
      HttpRequest req, HttpResponse resp, HttpRequest originalRequest) {
    try {
      doOutgoingResponseLogging(resp);
      return handleResponse(req, resp, originalRequest);
    } catch (RuntimeException e) {
      log.warn("Uncaught exception during handling of response", e);
      propagateExceptionMessageSafe(e);
      throw e;
    }
  }

  private HttpResponse handleResponse(
      HttpRequest req, HttpResponse resp, HttpRequest originalRequest) {
    rewriteLocationHeaderIfApplicable(resp);
    applyModifications(originalRequest, resp);
    if (shouldLogTraffic()) {
      parseMessages(req, resp);
    }
    return resp.withBody(resp.getBody());
  }

  private void rewriteLocationHeaderIfApplicable(HttpResponse resp) {
    if (tigerProxy.getTigerProxyConfiguration().isRewriteLocationHeader()
        && resp.getStatusCode() / 100 == 3
        && !resp.getHeader(LOCATION_HEADER_KEY).isEmpty()) {
      final List<String> locations = resp.getHeader(LOCATION_HEADER_KEY);
      resp.removeHeader(LOCATION_HEADER_KEY);
      locations.stream()
          .map(this::rewriteConcreteLocation)
          .map(l -> new Header(LOCATION_HEADER_KEY, l))
          .forEach(resp::withHeader);
      log.info("Rewriting from {} to {}", locations, resp.getHeader(LOCATION_HEADER_KEY));
    }
  }

  protected String rewriteConcreteLocation(String originalLocation) {
    return originalLocation;
  }

  public void parseMessage(HttpRequest mockServerRequest) {
    val messageParsingCompleteFuture = executeHttpRequestParsing(mockServerRequest);
    waitForMessageToBeParsedIfConfigured(messageParsingCompleteFuture);
  }

  private void parseMessages(HttpRequest req, HttpResponse resp) {
    val messageParsingCompleteFuture = executeHttpTrafficPairParsing(req, resp);
    waitForMessageToBeParsedIfConfigured(messageParsingCompleteFuture);
  }

  @SneakyThrows
  private void waitForMessageToBeParsedIfConfigured(
      CompletableFuture messageParsingCompleteFuture) {
    if (tigerProxy.getTigerProxyConfiguration().isParsingShouldBlockCommunication()) {
      try {
        messageParsingCompleteFuture.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new TigerProxyParsingException("Interruption while parsing traffic", e);
      } catch (ExecutionException e) {
        throw new TigerProxyParsingException("Error while parsing traffic", e);
      }
    }
  }

  public CompletableFuture<RbelElement> executeHttpRequestParsing(HttpRequest request) {
    if (isHealthEndpointRequest(request)) {
      return CompletableFuture.completedFuture(null);
    }

    addServerNameForSender(request);

    return getTigerProxy()
        .getMockServerToRbelConverter()
        .convertRequest(
            request,
            extractProtocolAndHostForRequest(request),
            Optional.of(ZonedDateTime.now()),
            previousMessageUuid)
        .exceptionally(
            e -> {
              log.error("Error while parsing request", e);
              return null;
            });
  }

  private CompletableFuture<RbelElement> executeHttpTrafficPairParsing(
      HttpRequest request, HttpResponse response) {
    if (isHealthEndpointRequest(request)) {
      return CompletableFuture.completedFuture(null);
    }

    addServerNameForSender(request);

    return getTigerProxy()
        .getMockServerToRbelConverter()
        .convertResponse(
            request,
            response,
            extractProtocolAndHostForRequest(request),
            request.getSenderAddress(),
            Optional.of(ZonedDateTime.now()),
            previousMessageUuid)
        .exceptionally(
            e -> {
              log.error("Error while parsing response", e);
              getTigerProxy().propagateException(e);
              return null;
            });
  }

  private boolean isHealthEndpointRequest(HttpRequest request) {
    return request.getQueryStringParameters() != null
        && request
            .getQueryStringParameters()
            .containsEntry(
                "healthEndPointUuid", getTigerProxy().getHealthEndpointRequestUuid().toString());
  }

  boolean shouldLogTraffic() {
    return !getTigerRoute().isDisableRbelLogging();
  }

  protected abstract String extractProtocolAndHostForRequest(HttpRequest request);

  HttpRequest cloneRequest(HttpRequest req) {
    final HttpOverrideForwardedRequest clonedRequest = forwardOverriddenRequest(req);
    if (req.getBody() != null) {
      return clonedRequest.getRequestOverride().withBody(req.getBody());
    } else {
      return clonedRequest.getRequestOverride();
    }
  }

  public void doOutgoingResponseLogging(HttpResponse resp) {
    if (log.isInfoEnabled() && tigerProxy.getTigerProxyConfiguration().isActivateTrafficLogging()) {
      log.info(
          "Returning HTTP "
              + resp.getStatusCode()
              + " Response-Length: "
              + getMessageSize(resp.getBody()));
    }
  }

  private static String getMessageSize(byte[] body) {
    return FileUtils.byteCountToDisplaySize(body.length);
  }

  public void doIncomingRequestLogging(HttpRequest req) {
    if (log.isInfoEnabled() && tigerProxy.getTigerProxyConfiguration().isActivateTrafficLogging()) {
      log.info("Received " + req.printLogLineDescription() + " => " + printTrafficTarget(req));
    }
  }

  protected abstract String printTrafficTarget(HttpRequest req);

  @Override
  public boolean matches(HttpRequest request) {
    if (tigerRoute == null
        || tigerRoute.getCriterions() == null
        || tigerRoute.getCriterions().isEmpty()) {
      return true;
    }
    final RbelElement convertedRequest = getConvertedRequest(request);
    return tigerRoute.getCriterions().stream()
        .allMatch(
            criterion -> {
              final boolean matches =
                  TigerJexlExecutor.matchesAsJexlExpression(convertedRequest, criterion);
              log.atTrace()
                  .addArgument(criterion)
                  .addArgument(convertedRequest::printHttpDescription)
                  .addArgument(() -> matches)
                  .log("Matching {} for {}: {}");
              return matches;
            });
  }

  private RbelElement getConvertedRequest(HttpRequest request) {
    var convertedRequest = request.getCorrespondingRbelMessage();
    if (convertedRequest == null) {
      var rbelMessage =
          getTigerProxy().getMockServerToRbelConverter().requestToRbelMessage(request);
      convertedRequest =
          getTigerProxy()
              .getRbelLogger()
              .getRbelConverter()
              .convertElement(
                  rbelMessage,
                  List.of(
                      RbelConversionPhase.PREPARATION,
                      RbelConversionPhase.PROTOCOL_PARSING,
                      RbelConversionPhase.CONTENT_PARSING));
      request.setCorrespondingRbelMessage(convertedRequest);
    }
    return convertedRequest;
  }

  @Override
  public Action handleException(Throwable exception, HttpRequest request) {
    final TigerProxyRoutingException routingException =
        new TigerProxyRoutingException(
            "Exception during handling of HTTP request: " + exception.getMessage(),
            // sender and receiver are switched here, because the exception acts as a response
            Optional.ofNullable(request.getReceiverAddress())
                .map(SocketAddress::toRbelHostname)
                .orElse(null),
            RbelHostname.fromString(request.getSenderAddress()).orElse(null),
            exception);
    routingException.setRoutedMessage(request.getParsedMessageFuture());
    log.info(routingException.getMessage(), routingException);

    addServerNameForSender(request);
    tigerProxy
        .getMockServerToRbelConverter()
        .convertErrorResponse(
            request,
            extractProtocolAndHostForRequest(request),
            routingException,
            previousMessageUuid);
    return new CloseChannel();
  }

  private void addServerNameForSender(HttpRequest request) {
    tigerProxy
        .getName()
        .filter(name -> !name.equals("local_tiger_proxy"))
        .ifPresent(
            serverName ->
                RbelHostname.fromString(request.getSenderAddress())
                    .ifPresent(
                        sender ->
                            GlobalServerMap.addServerNameForPort(sender.getPort(), serverName)));
  }
}
