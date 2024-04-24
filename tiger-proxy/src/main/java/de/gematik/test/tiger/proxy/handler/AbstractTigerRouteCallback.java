/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.facet.RbelNoteFacet.NoteStyling;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.certificate.TlsFacet;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyModificationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyParsingException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import lombok.Data;
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
public abstract class AbstractTigerRouteCallback implements ExpectationForwardAndResponseCallback {

  public static final String LOCATION_HEADER_KEY = "Location";
  private final TigerProxy tigerProxy;
  private final TigerRoute tigerRoute;
  private BundledServerNamesAdder bundledServerNamesAdder = new BundledServerNamesAdder();

  // Maps the Log-IDs to the (to be parsed) Rbel-messages
  private Map<String, CompletableFuture<RbelElement>> requestLogIdToParsingFuture =
      new ConcurrentHashMap<>();

  protected AbstractTigerRouteCallback(TigerProxy tigerProxy, TigerRoute tigerRoute) {
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

  public void applyModifications(HttpResponse response) {
    if (!tigerProxy.getModifications().isEmpty()) {
      parseMessageAndApplyModifications(response);
    }
  }

  public void parseMessageAndApplyModifications(HttpResponse response) {
    final RbelElement responseElement =
        tigerProxy
            .getRbelLogger()
            .getRbelConverter()
            .convertElement(
                tigerProxy.getMockServerToRbelConverter().responseToRbelMessage(response));
    final RbelElement modifiedResponse =
        tigerProxy.getRbelLogger().getRbelModifier().applyModifications(responseElement);
    if (modifiedResponse == responseElement) {
      return;
    }
    response.withBody(extractSafe(modifiedResponse, "$.body").getRawContent());
    for (RbelElement modifiedHeader : modifiedResponse.findRbelPathMembers("$.header.*")) {
      response =
          response.replaceHeader(
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
  public final HttpResponse handle(HttpRequest req, HttpResponse resp) {
    try {
      doOutgoingResponseLogging(resp);
      return handleResponse(req, resp);
    } catch (RuntimeException e) {
      log.warn("Uncaught exception during handling of response", e);
      propagateExceptionMessageSafe(e);
      throw e;
    }
  }

  private HttpResponse handleResponse(HttpRequest req, HttpResponse resp) {
    rewriteLocationHeaderIfApplicable(resp);
    applyModifications(resp);
    if (shouldLogTraffic()) {
      parseMessages(req, resp);
    }
    return resp.withBody(resp.getBodyAsRawBytes());
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
    executeHttpRequestParsing(mockServerRequest);
  }

  private void parseMessages(HttpRequest req, HttpResponse resp) {
    final CompletableFuture<Void> messageParsingCompleteFuture =
        executeHttpTrafficPairParsing(req, resp);
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

  public CompletableFuture<RbelElement> executeHttpRequestParsing(HttpRequest mockServerRequest) {
    if (isHealthEndpointRequest(mockServerRequest)) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<RbelElement> toBeConvertedRequest =
        getTigerProxy()
            .getMockServerToRbelConverter()
            .convertRequest(
                mockServerRequest,
                extractProtocolAndHostForRequest(mockServerRequest),
                Optional.of(ZonedDateTime.now()));

    requestLogIdToParsingFuture.put(mockServerRequest.getLogCorrelationId(), toBeConvertedRequest);
    log.trace("Added request to pairingMap with id {}", mockServerRequest.getLogCorrelationId());

    return toBeConvertedRequest
        .thenApply(
            request -> {
              parseCertificateChainIfPresent(mockServerRequest, request)
                  .ifPresent(request::addFacet);
              addBundledServerNameToHostnameFacet(request);
              getTigerProxy().triggerListener(request);
              return request;
            })
        .exceptionally(
            e -> {
              log.error("Error while parsing request", e);
              return null;
            });
  }

  private CompletableFuture<Void> executeHttpTrafficPairParsing(
      HttpRequest req, HttpResponse resp) {
    if (isHealthEndpointRequest(req)) {
      return CompletableFuture.completedFuture(null);
    }

    return getTigerProxy()
        .getMockServerToRbelConverter()
        .convertResponse(
            resp,
            extractProtocolAndHostForRequest(req),
            req.getRemoteAddress(),
            Optional.of(ZonedDateTime.now()))
        .thenAccept(
            response ->
                retrieveParsedRequest(req)
                    .thenAccept(request -> postProcessingAfterBothMessageParsed(response, request))
                    .exceptionally(
                        e -> {
                          log.error("Error while both processing message pair", e);
                          return null;
                        }))
        .exceptionally(
            e -> {
              log.error("Error while parsing response", e);
              return null;
            });
  }

  private void postProcessingAfterBothMessageParsed(RbelElement response, RbelElement request) {
    val pairFacet = TracingMessagePairFacet.builder().response(response).request(request).build();
    request.addFacet(pairFacet);
    response.addFacet(pairFacet);
    response.addOrReplaceFacet(
        response
            .getFacet(RbelHttpResponseFacet.class)
            .map(RbelHttpResponseFacet::toBuilder)
            .orElse(RbelHttpResponseFacet.builder())
            .request(request)
            .build());
    request
        .getFacet(TlsFacet.class)
        .ifPresent(
            tlsFacet -> {
              var respTlsFacet =
                  new TlsFacet(
                      tlsFacet
                          .getTlsVersion()
                          .seekValue(String.class)
                          .map(value -> RbelElement.wrap(response, value))
                          .orElse(null),
                      tlsFacet
                          .getCipherSuite()
                          .seekValue(String.class)
                          .map(value -> RbelElement.wrap(response, value))
                          .orElse(null),
                      null);
              response.addFacet(respTlsFacet);
            });
    addBundledServerNameToHostnameFacet(response);

    getTigerProxy().triggerListener(response);
  }

  private CompletableFuture<RbelElement> retrieveParsedRequest(HttpRequest req) {
    CompletableFuture<RbelElement> requestParsingFuture =
        requestLogIdToParsingFuture.remove(req.getLogCorrelationId());

    if (requestParsingFuture == null) {
      log.error(
          "Could not find request for response with id {}! Skipping response parsing!",
          req.getLogCorrelationId());
      tigerProxy.getRbelMessages().forEach(msg -> log.info("Message: {} : {}", msg.getUuid(), msg));
      throw new TigerProxyParsingException(
          "Not able to find parsed request for uuid " + req.getLogCorrelationId() + "!");
    }

    return requestParsingFuture;
  }

  private void addBundledServerNameToHostnameFacet(RbelElement element) {
    bundledServerNamesAdder.addBundledServerNameToHostnameFacet(element);
  }

  private boolean isHealthEndpointRequest(HttpRequest request) {
    return request.getQueryStringParameters() != null
        && request
            .getQueryStringParameters()
            .containsEntry(
                "healthEndPointUuid", getTigerProxy().getHealthEndpointRequestUuid().toString());
  }

  private Optional<RbelFacet> parseCertificateChainIfPresent(
      HttpRequest httpRequest, RbelElement message) {
    if (StringUtils.isBlank(httpRequest.getTlsVersion())) {
      return Optional.empty();
    }
    if (httpRequest.getClientCertificateChain() == null
        || httpRequest.getClientCertificateChain().isEmpty()) {
      return Optional.of(
          new TlsFacet(
              RbelElement.wrap(message, httpRequest.getTlsVersion()),
              RbelElement.wrap(message, httpRequest.getCipherSuite()),
              null));
    }
    RbelElement certificateChainElement = new RbelElement(null, message);
    certificateChainElement.addFacet(
        RbelListFacet.builder()
            .childNodes(
                httpRequest.getClientCertificateChain().stream()
                    .map(MockserverX509CertificateWrapper::certificate)
                    .map(cert -> mapToRbelElement(cert, message))
                    .toList())
            .build());

    return Optional.of(
        new TlsFacet(
            RbelElement.wrap(message, httpRequest.getTlsVersion()),
            RbelElement.wrap(message, httpRequest.getCipherSuite()),
            certificateChainElement));
  }

  private RbelElement mapToRbelElement(Certificate certificate, RbelElement parentNode) {
    try {
      final RbelElement certificateNode = new RbelElement(certificate.getEncoded(), parentNode);
      getTigerProxy().getRbelLogger().getRbelConverter().convertElement(certificateNode);
      return certificateNode;
    } catch (CertificateEncodingException e) {
      final RbelElement rbelElement = new RbelElement(null, parentNode);
      rbelElement.addFacet(
          RbelNoteFacet.builder()
              .style(NoteStyling.ERROR)
              .value(
                  "Error while trying to get binary representation for certificate: "
                      + e.getMessage())
              .build());
      return rbelElement;
    }
  }

  boolean shouldLogTraffic() {
    return !getTigerRoute().isDisableRbelLogging();
  }

  protected abstract String extractProtocolAndHostForRequest(HttpRequest request);

  HttpRequest cloneRequest(HttpRequest req) {
    final HttpOverrideForwardedRequest clonedRequest = forwardOverriddenRequest(req);
    if (req.getBody() != null) {
      return clonedRequest.getRequestOverride().withBody(req.getBodyAsRawBytes());
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
              + getMessageSize(resp.getBodyAsRawBytes()));
    }
  }

  private static String getMessageSize(byte[] body) {
    return FileUtils.byteCountToDisplaySize(body.length);
  }

  public void doIncomingRequestLogging(HttpRequest req) {
    if (log.isInfoEnabled() && tigerProxy.getTigerProxyConfiguration().isActivateTrafficLogging()) {
      log.info(
          "Received "
              + getProtocol(req)
              + " "
              + req.getMethod()
              + " "
              + req.getPath()
              + " "
              + getUserAgentString(req)
              + " Request-Length: "
              + getMessageSize(req.getBodyAsRawBytes())
              + " => "
              + printTrafficTarget(req));
    }
  }

  protected abstract String printTrafficTarget(HttpRequest req);

  private static String getProtocol(HttpRequest req) {
    if (Boolean.TRUE.equals(req.isSecure())) {
      return "HTTPS";
    } else {
      return "HTTP";
    }
  }

  private static String getUserAgentString(HttpRequest req) {
    return Optional.ofNullable(req.getFirstHeader("User-Agent"))
        .filter(StringUtils::isNotEmpty)
        .map(agent -> "User-Agent: '" + agent + "'")
        .orElse("");
  }

  @Override
  public boolean matches(HttpRequest request) {
    if (tigerRoute == null
        || tigerRoute.getCriterions() == null
        || tigerRoute.getCriterions().isEmpty()) {
      return true;
    }
    final RbelElement convertedRequest =
        getTigerProxy()
            .getMockServerToRbelConverter()
            .convertRequest(
                request,
                extractProtocolAndHostForRequest(request),
                Optional.of(ZonedDateTime.now()))
            .join();
    request.setParsedRbelMessage(convertedRequest);
    return tigerRoute.getCriterions().stream()
        .allMatch(
            criterion -> {
              final boolean matches =
                  TigerJexlExecutor.matchesAsJexlExpression(convertedRequest, criterion);
              log.trace(
                  "Matching {} for {}: {}",
                  criterion,
                  convertedRequest.printHttpDescription(),
                  matches);
              return matches;
            });
  }

  @Override
  public Action handleException(Throwable exception, HttpRequest request) {
    log.info("Exception during handling of request", exception);
    final RbelElement errorResponse =
        tigerProxy
            .getMockServerToRbelConverter()
            .convertErrorResponse(request, extractProtocolAndHostForRequest(request));
    errorResponse.addFacet(
        RbelNoteFacet.builder().style(NoteStyling.ERROR).value(exception.getMessage()).build());
    return new CloseChannel();
  }
}
