/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.zion.services;

import static de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition.PathMatchingResult.EMPTY_MATCH;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelSerializationResult;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.zion.ZionException;
import de.gematik.test.tiger.zion.config.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.http.*;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.server.ResponseStatusException;

@Builder
@Slf4j
public class ZionRequestExecutor {

  private static final String CONSIDERING_RESPONSE = "Considering response {} {}";
  @NonNull private final RbelElement requestRbelMessage;
  private final int localServerPort;
  private final LocalDateTime responseStartTime;
  @NonNull private final RbelHostname clientHostname;
  @NonNull private final RbelHostname serverHostname;
  @NonNull private final ObjectMapper objectMapper;
  @NonNull private final RbelLogger rbelLogger;
  @NonNull private final ZionConfiguration configuration;
  @NonNull private final RequestEntity<byte[]> request;
  @NonNull private final RbelWriter rbelWriter;
  @NonNull private final BackendRequestExecutor backendRequestExecutor;

  public ResponseEntity<byte[]> execute() {
    var mainContext =
        new TigerJexlContext()
            .with("zion.port", String.valueOf(localServerPort))
            .withRootElement(requestRbelMessage);
    final Optional<Pair<TigerMockResponse, TigerJexlContext>> configuredResponse =
        findResponseForGivenRequest(requestRbelMessage, mainContext);
    if (configuredResponse.isPresent()) {
      TigerMockResponse chosenResponse = configuredResponse.get().getLeft();
      TigerJexlContext responseContext = configuredResponse.get().getRight();
      final ResponseEntity<byte[]> responseEntity = renderResponse(chosenResponse, responseContext);
      responseContext.allNonStandardValues().forEach(TigerGlobalConfiguration::putValue);
      parseResponseWithRbelLogger(responseEntity);
      delayResponseIfNecessary(chosenResponse);
      return responseEntity;
    } else {
      return spyWithRemoteServer(request)
          .orElseThrow(
              () -> {
                log.warn("Could not match request \n{}", requestRbelMessage.printTreeStructure());
                return new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No suitable return value found");
              });
    }
  }

  private void delayResponseIfNecessary(TigerMockResponse chosenResponse) {
    final long actualMillis = Duration.between(responseStartTime, LocalDateTime.now()).toMillis();
    final String responseDelay = chosenResponse.getResponse().getResponseDelay();
    if (StringUtils.isEmpty(responseDelay)) {
      return;
    }

    final long necessaryDelay =
        Long.parseLong(TigerGlobalConfiguration.resolvePlaceholders(responseDelay)) - actualMillis;
    if (necessaryDelay > 0) {
      try {
        Thread.sleep(necessaryDelay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ZionException("Interrupt received", e);
      }
    }
  }

  private Optional<Pair<TigerMockResponse, TigerJexlContext>> findResponseForGivenRequest(
      RbelElement requestRbelMessage, TigerJexlContext context) {
    for (TigerMockResponse response :
        configuration.getMockResponses().values().stream()
            .sorted(Comparator.comparing(TigerMockResponse::getImportance).reversed())
            .toList()) {
      if (response.getResponse() != null) {
        log.trace(
            CONSIDERING_RESPONSE,
            response.getResponse().getStatusCode(),
            response.getResponse().getBody());
      } else {
        log.trace(
            "Considering response without body, nested responses: {}",
            response.getNestedResponses().keySet());
      }
      final TigerJexlContext localResponseContext =
          context
              .withCurrentElement(requestRbelMessage)
              .withRootElement(requestRbelMessage)
              .withShouldIgnoreEmptyRbelPaths(true);
      final Optional<Pair<TigerMockResponse, TigerJexlContext>> responseCandidate =
          findMatchingResponse(response, requestRbelMessage, localResponseContext);
      // responseCandidate is not necessarily equal to response: nestedResponses!
      if (responseCandidate.isPresent()) {
        return responseCandidate;
      }
    }
    return Optional.empty();
  }

  private ResponseEntity<byte[]> renderResponse(
      TigerMockResponse response, TigerJexlContext context) {
    final Optional<RbelSerializationResult> serializationResult =
        renderResponseBody(response, context);

    final BodyBuilder responseBuilder =
        ResponseEntity.status(
            Integer.parseInt(
                TigerGlobalConfiguration.resolvePlaceholders(
                    response.getResponse().getStatusCode())));
    serializationResult
        .flatMap(RbelSerializationResult::getMediaType)
        .map(Object::toString)
        .map(MediaType::parseMediaType)
        .ifPresent(responseBuilder::contentType);

    for (Entry<String, String> entry : response.getResponse().getHeaders().entrySet()) {
      final RbelElement convertedElement =
          rbelLogger.getRbelConverter().convertElement(entry.getValue(), null);
      final RbelSerializationResult serialized =
          rbelWriter.serialize(
              convertedElement, new TigerJexlContext().withRootElement(requestRbelMessage));
      String headerValue = serialized.getContentAsString();
      if (StringUtils.isNotEmpty(headerValue)) {
        responseBuilder.header(entry.getKey(), serialized.getContentAsString());
      }
    }

    return responseBuilder.body(
        serializationResult.map(RbelSerializationResult::getContent).orElse(null));
  }

  private Optional<Pair<TigerMockResponse, TigerJexlContext>> findMatchingResponse(
      TigerMockResponse mockResponse, RbelElement requestRbelMessage, TigerJexlContext context) {
    VariableAssigner.doAssignments(mockResponse.getAssignments(), requestRbelMessage, context);
    executeBackendRequestsBeforeDecision(mockResponse, context);
    if (!doesItMatch(mockResponse, context)) {
      if (log.isTraceEnabled()) {
        log.trace(
            "Discarding response {} with criterions {} for message {}",
            mockResponse.getName(),
            mockResponse.getRequestCriterions(),
            requestRbelMessage.printTreeStructureWithoutColors());
      }
      return Optional.empty();
    }
    Optional<Pair<TigerMockResponse, TigerJexlContext>> foundResponse;
    if (mockResponse.getResponse() != null) {
      log.trace(
          CONSIDERING_RESPONSE,
          mockResponse.getResponse().getStatusCode(),
          mockResponse.getResponse().getBody());
      foundResponse = Optional.of(Pair.of(mockResponse, context));
    } else {
      foundResponse =
          Optional.ofNullable(mockResponse.getNestedResponses()).map(Map::values).stream()
              .flatMap(Collection::stream)
              .sorted(Comparator.comparing(TigerMockResponse::getImportance).reversed())
              .map(r -> findMatchingResponse(r, requestRbelMessage, context.cloneContext()))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .peek( // NOSONAR - used for logging
                  respPair ->
                      log.trace(
                          CONSIDERING_RESPONSE,
                          respPair.getKey().getResponse().getStatusCode(),
                          respPair.getKey().getResponse().getBody()))
              .findFirst();
    }
    if (foundResponse.isPresent()) {
      executeBackendRequestsAfterDecision(mockResponse, context);
    }
    return foundResponse;
  }

  private boolean doesItMatch(TigerMockResponse mockResponse, TigerJexlContext context) {
    List<String> combinedRequestCriterions = new ArrayList<>(mockResponse.getRequestCriterions());

    mockResponse
        .getRequestOptional()
        .map(ZionRequestMatchDefinition::extractAdditionalCriteria)
        .ifPresent(combinedRequestCriterions::addAll);

    RbelElement currentRequestRbelMessage = (RbelElement) context.getCurrentElement();
    ZionRequestMatchDefinition.PathMatchingResult pathMatchingResult =
        mockResponse
            .getRequestOptional()
            .map(r -> r.matchPathVariables(currentRequestRbelMessage, context))
            .orElse(EMPTY_MATCH);

    if (EMPTY_MATCH.equals(pathMatchingResult) && combinedRequestCriterions.isEmpty()) {
      return true;
    }

    VariableAssigner.doAssignments(
        pathMatchingResult.capturedVariables(), currentRequestRbelMessage, context);

    for (String requestCriterion : combinedRequestCriterions) {
      final boolean criterionMatches =
          TigerJexlExecutor.matchesAsJexlExpression(
              TigerGlobalConfiguration.resolvePlaceholdersWithContext(requestCriterion, context),
              context);
      if (!criterionMatches) {
        log.trace(
            "Criterion does not match for response {}: {}",
            mockResponse.getName(),
            requestCriterion);
        return false;
      }
    }
    if (pathMatchingResult.doesItMatch()) {
      return true;
    } else {
      log.trace("Path does not match for response {}", mockResponse.getName());
      return false;
    }
  }

  private void executeBackendRequestsBeforeDecision(
      TigerMockResponse mockResponse, TigerJexlContext jexlContext) {
    List<ZionBackendRequestDescription> beforeDecision =
        mockResponse.getBackendRequests().values().stream()
            .filter(r -> !r.isExecuteAfterSelection())
            .toList();

    backendRequestExecutor.executeBackendRequests(beforeDecision, jexlContext, requestRbelMessage);
  }

  private void executeBackendRequestsAfterDecision(
      TigerMockResponse mockResponse, TigerJexlContext jexlContext) {
    List<ZionBackendRequestDescription> afterDecision =
        mockResponse.getBackendRequests().values().stream()
            .filter(ZionBackendRequestDescription::isExecuteAfterSelection)
            .toList();

    backendRequestExecutor.executeBackendRequests(afterDecision, jexlContext, requestRbelMessage);
  }

  @SneakyThrows
  private Optional<ResponseEntity<byte[]>> spyWithRemoteServer(RequestEntity<byte[]> request) {
    if (configuration.getSpy() == null) {
      return Optional.empty();
    }
    final URI targetUri =
        new URIBuilder(configuration.getSpy().getUrl())
            .setPath(request.getUrl().getPath())
            .setQuery(request.getUrl().getQuery())
            .build();

    final String name = Optional.ofNullable(request.getMethod()).map(HttpMethod::name).orElse("");
    final HttpRequestWithBody unirestRequest =
        Unirest.request(name, targetUri.toString())
            .headers(
                request.getHeaders().entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            Entry::getKey, header -> String.join(",", header.getValue()))));
    if (request.hasBody()) {
      unirestRequest.body(request.getBody());
    }
    final HttpResponse<byte[]> unirestResponse = unirestRequest.asBytes();
    final ResponseEntity<byte[]> responseEntity =
        ResponseEntity.status(unirestResponse.getStatus()).body(unirestResponse.getBody());

    parseResponseWithRbelLogger(responseEntity);
    final RbelElement responseRbelMessage = rbelLogger.getMessageHistory().getLast();
    final TigerMockResponse mockResponse =
        TigerMockResponse.builder()
            .requestCriterions(
                List.of(
                    "message.method == '" + name + "'",
                    "message.url =$ '" + getUriEnding(targetUri) + "'"))
            .response(
                TigerMockResponseDescription.builder()
                    .body(
                        responseRbelMessage
                            .getFirst("body")
                            .map(
                                bodyElement ->
                                    rbelWriter.serializeWithEnforcedContentType(
                                        bodyElement, RbelContentType.JSON, new TigerJexlContext()))
                            .map(RbelSerializationResult::getContentAsString)
                            .orElse(null))
                    .build())
            .build();
    FileUtils.writeStringToFile(
        Path.of(configuration.getSpy().getProtocolToPath(), "spy_" + UUID.randomUUID() + ".yaml")
            .toFile(),
        objectMapper.writeValueAsString(mockResponse),
        Charset.defaultCharset());

    return Optional.of(responseEntity);
  }

  private String getUriEnding(URI targetUri) {
    if (StringUtils.isEmpty(targetUri.getQuery())) {
      return targetUri.getPath();
    } else {
      return targetUri.getPath() + "?" + targetUri.getQuery();
    }
  }

  private void parseResponseWithRbelLogger(ResponseEntity<byte[]> el) {
    rbelLogger
        .getRbelConverter()
        .parseMessage(
            buildRawMessageApproximate(el),
            serverHostname,
            clientHostname,
            Optional.of(ZonedDateTime.now()));
  }

  private byte[] buildRawMessageApproximate(ResponseEntity<byte[]> response) {
    String header = "HTTP/1.1 " + response.getStatusCode().value();
    if (!response.getHeaders().isEmpty()) {
      header +=
          "\r\n"
              + response.getHeaders().entrySet().stream()
                  .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                  .collect(Collectors.joining("\r\n"));
    }
    header += "\r\n\r\n";
    if (response.hasBody()) {
      return ArrayUtils.addAll(header.getBytes(), response.getBody());
    } else {
      return header.getBytes();
    }
  }

  private Optional<RbelSerializationResult> renderResponseBody(
      TigerMockResponse response, TigerJexlContext context) {
    return Optional.ofNullable(response.getResponse().getBody())
        .map(
            s ->
                rbelWriter.serialize(
                    rbelLogger.getRbelConverter().convertElement(s, null), context));
  }
}
