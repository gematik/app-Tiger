/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.decorator.AddBundledServerNamesModifier;
import de.gematik.rbellogger.data.decorator.MessageMetadataModifier;
import de.gematik.rbellogger.data.decorator.ServerNameFromHostname;
import de.gematik.rbellogger.data.decorator.ServernameFromProcessAndPortSupplier;
import de.gematik.rbellogger.data.decorator.ServernameFromSpyPortMapping;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelListFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.certificate.TlsFacet;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyModificationException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final Map<String, ZonedDateTime> requestTimingMap = new HashMap<>();
  private final MessageMetadataModifier modifierBasedOnProcessAndPort;
  private final MessageMetadataModifier modifierBasedOnHostname;
  private final MessageMetadataModifier modifierBasedOnlyOnPort;

  protected AbstractTigerRouteCallback(TigerProxy tigerProxy, TigerRoute tigerRoute) {
    this.tigerProxy = tigerProxy;
    this.tigerRoute = tigerRoute;
    this.modifierBasedOnProcessAndPort =
        AddBundledServerNamesModifier.createModifier(new ServernameFromProcessAndPortSupplier());
    this.modifierBasedOnHostname =
        AddBundledServerNamesModifier.createModifier(new ServerNameFromHostname());
    this.modifierBasedOnlyOnPort =
        AddBundledServerNamesModifier.createModifier(new ServernameFromSpyPortMapping());
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
    request.withPath(uriFacet.getBasicPathString());
    clearExistingQueryParameters(request);
    addAllQueryParametersFromRbelMessage(request, uriFacet);
    request.withMethod(extractSafe(modifiedRequest, "$.method").getRawStringContent());
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
      requestTimingMap.put(req.getLogCorrelationId(), ZonedDateTime.now());
      return handleRequest(req);
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
    requestTimingMap.remove(req.getLogCorrelationId());
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

  private void parseMessages(HttpRequest req, HttpResponse resp) {
    final Optional<ZonedDateTime> requestTime =
        Optional.ofNullable(requestTimingMap.remove(req.getLogCorrelationId()));
    if (getTigerProxy().getTigerProxyConfiguration().isParsingShouldBlockCommunication()) {
      executeHttpTrafficPairParsing(req, resp, requestTime);
    } else {
      getTigerProxy()
          .getTrafficParserExecutor()
          .submit(() -> executeHttpTrafficPairParsing(req, resp, requestTime));
    }
  }

  private void executeHttpTrafficPairParsing(
      HttpRequest req, HttpResponse resp, Optional<ZonedDateTime> requestTime) {
    try {
      if (isHealthEndpointRequest(req)) {
        return;
      }

      final RbelElement request =
          getTigerProxy()
              .getMockServerToRbelConverter()
              .convertRequest(req, extractProtocolAndHostForRequest(req), requestTime);
      final RbelElement response =
          getTigerProxy()
              .getMockServerToRbelConverter()
              .convertResponse(resp, extractProtocolAndHostForRequest(req), req.getRemoteAddress());
      addTimingFacet(response, ZonedDateTime.now());
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

      parseCertificateChainIfPresent(req, request).ifPresent(request::addFacet);

      getTigerProxy().triggerListener(request);
      getTigerProxy().triggerListener(response);

      addBundledServerNameToHostnameFacet(request);
      addBundledServerNameToHostnameFacet(response);
    } catch (RuntimeException e) {
      propagateExceptionMessageSafe(e);
      log.error("Rbel-parsing failed!", e);
    }
  }

  private void addBundledServerNameToHostnameFacet(RbelElement element) {
    // order is important!!
    modifierBasedOnHostname.modifyMetadata(element);
    modifierBasedOnlyOnPort.modifyMetadata(element);
    modifierBasedOnProcessAndPort.modifyMetadata(element);
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
    if (httpRequest.getClientCertificateChain() == null
        || httpRequest.getClientCertificateChain().isEmpty()) {
      return Optional.empty();
    }
    RbelElement certificateChainElement = new RbelElement(null, message);
    certificateChainElement.addFacet(
        RbelListFacet.builder()
            .childNodes(
                httpRequest.getClientCertificateChain().stream()
                    .map(X509Certificate::getCertificate)
                    .map(cert -> mapToRbelElement(cert, message))
                    .toList())
            .build());

    return Optional.of(TlsFacet.builder().clientCertificateChain(certificateChainElement).build());
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

  private void addTimingFacet(RbelElement message, ZonedDateTime requestTime) {
    message.addFacet(RbelMessageTimingFacet.builder().transmissionTime(requestTime).build());
  }

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
}
