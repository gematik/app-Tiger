package de.gematik.test.tiger.zion.services;

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
import de.gematik.test.tiger.zion.config.*;
import kong.unirest.Headers;
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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition.PathMatchingResult.EMPTY_MATCH;

@Builder
@Slf4j
public class ZionRequestExecutor {

    private static final String CONSIDERING_RESPONSE = "Considering response {} {}";
    @NonNull
    private final RbelElement requestRbelMessage;
    private final int localServerPort;
    @NonNull
    private final RbelHostname clientHostname;
    @NonNull
    private final RbelHostname serverHostname;
    @NonNull
    private final ObjectMapper objectMapper;
    @NonNull
    private final RbelLogger rbelLogger;
    @NonNull
    private final ZionConfiguration configuration;
    @NonNull
    private final RequestEntity<byte[]> request;
    @NonNull
    private final RbelWriter rbelWriter;

    public ResponseEntity<byte[]> execute() {
        var mainContext = new TigerJexlContext()
            .with("zion.port", String.valueOf(localServerPort))
            .withRootElement(requestRbelMessage);
        final Optional<Pair<TigerMockResponse, TigerJexlContext>> configuredResponse = findResponseForGivenRequest(requestRbelMessage, mainContext);
        if (configuredResponse.isPresent()) {
            TigerMockResponse chosenResponse = configuredResponse.get().getLeft();
            TigerJexlContext responseContext = configuredResponse.get().getRight();
            final ResponseEntity<byte[]> responseEntity = renderResponse(chosenResponse, responseContext);
            return parseResponseWithRbelLogger(responseEntity);
        } else {
            return spyWithRemoteServer(request).orElseThrow(() -> {
                log.warn("Could not match request \n{}", requestRbelMessage.printTreeStructure());
                return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No suitable return value found");
            });
        }
    }

    private Optional<Pair<TigerMockResponse, TigerJexlContext>> findResponseForGivenRequest(RbelElement requestRbelMessage, TigerJexlContext context) {
        for (TigerMockResponse response : configuration.getMockResponses().values().stream()
            .sorted(Comparator.comparing(TigerMockResponse::getImportance).reversed())
            .toList()) {
            if (response.getResponse() != null) {
                log.trace(CONSIDERING_RESPONSE, response.getResponse().getStatusCode(), response.getResponse().getBody());
            } else {
                log.trace("Considering response without body, nested responses: {}", response.getNestedResponses().keySet());
            }
            final TigerJexlContext localResponseContext = context
                    .withCurrentElement(requestRbelMessage)
                    .withRootElement(requestRbelMessage);
            doAssignments(response.getAssignments(), requestRbelMessage, localResponseContext);
            executeBackendRequestsBeforeDecision(response, localResponseContext);
            final Optional<Pair<TigerMockResponse, TigerJexlContext>> responseCandidate = findMatchingResponse(response, requestRbelMessage,
                localResponseContext);
            // responseCandidate is not necessarily equal to response: nestedResponses!
            if (responseCandidate.isPresent()) {
                return responseCandidate;
            }
        }
        return Optional.empty();
    }

    private ResponseEntity<byte[]> renderResponse(TigerMockResponse response, TigerJexlContext context) {
        final Optional<RbelSerializationResult> serializationResult = renderResponseBody(response, context);

        final BodyBuilder responseBuilder = ResponseEntity
            .status(response.getResponse().getStatusCode());
        serializationResult.flatMap(RbelSerializationResult::getMediaType)
            .map(Object::toString)
            .map(MediaType::parseMediaType)
            .ifPresent(responseBuilder::contentType);

        for (Entry<String, String> entry : response.getResponse().getHeaders().entrySet()) {
            final RbelElement convertedElement = rbelLogger.getRbelConverter().convertElement(entry.getValue(), null);
            final RbelSerializationResult serialized = rbelWriter.serialize(convertedElement, new TigerJexlContext().withRootElement(requestRbelMessage));
            if (serialized.getContent() != null) {
                responseBuilder.header(entry.getKey(), serialized.getContentAsString());
            } else {
                responseBuilder.header(entry.getKey(), "");
            }
        }

        return responseBuilder.body(serializationResult.map(RbelSerializationResult::getContent).orElse(null));
    }

    private void doAssignments(Map<String, String> assignments, RbelElement currentElement, TigerJexlContext jexlContext) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        final TigerJexlContext localResponseContext = jexlContext
            .withCurrentElement(currentElement)
            .withRootElement(currentElement);

        for (Entry<String, String> entry : assignments.entrySet()) {
            final String newValue = Optional.of(entry.getValue())
                .filter(s -> s.startsWith("$."))
                .flatMap(currentElement::findElement)
                .map(el -> el.seekValue(String.class)
                    .orElseGet(el::getRawStringContent))
                .map(TigerGlobalConfiguration::resolvePlaceholders)
                .orElseGet(() -> TigerGlobalConfiguration.resolvePlaceholdersWithContext(entry.getValue(), localResponseContext));

            final String key = TigerGlobalConfiguration.resolvePlaceholdersWithContext(entry.getKey(), localResponseContext);
            jexlContext.put(key, newValue);
        }
    }

    private Optional<Pair<TigerMockResponse, TigerJexlContext>> findMatchingResponse(TigerMockResponse mockResponse, RbelElement requestRbelMessage,
        TigerJexlContext context) {
        if (!doesItMatch(mockResponse, context)) {
            if (log.isTraceEnabled() && (mockResponse.getResponse() != null)) {
                log.trace("Discarding response {} {} with criterions {} for message {}",
                    mockResponse.getResponse().getStatusCode(), mockResponse.getResponse().getBody(),
                    mockResponse.getRequestCriterions(), requestRbelMessage.printTreeStructureWithoutColors());
            }
            return Optional.empty();
        }
        if (mockResponse.getResponse() != null) {
            log.trace(CONSIDERING_RESPONSE, mockResponse.getResponse().getStatusCode(), mockResponse.getResponse().getBody());
            return Optional.of(Pair.of(mockResponse, context));
        } else {
            return Optional.ofNullable(mockResponse.getNestedResponses())
                .map(Map::values)
                .stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(TigerMockResponse::getImportance).reversed())
                .map(r -> findMatchingResponse(r, requestRbelMessage, context.cloneContext()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .peek(respPair -> log.trace(CONSIDERING_RESPONSE,
                    respPair.getKey().getResponse().getStatusCode(),
                    respPair.getKey().getResponse().getBody()))
                .findFirst();
        }
    }

    private boolean doesItMatch(TigerMockResponse mockResponse, TigerJexlContext context) {
        List<String> combinedRequestCriterions = new ArrayList<>(mockResponse.getRequestCriterions());

        mockResponse.getRequestOptional().map(ZionRequestMatchDefinition::extractAdditionalCriteria).ifPresent(combinedRequestCriterions::addAll);

        RbelElement currentRequestRbelMessage = (RbelElement) context.getCurrentElement();
        ZionRequestMatchDefinition.PathMatchingResult pathMatchingResult = mockResponse.getRequestOptional()
                .map(r -> r.matchPathVariables(currentRequestRbelMessage, context)).orElse(EMPTY_MATCH);

        if (EMPTY_MATCH.equals(pathMatchingResult) && combinedRequestCriterions.isEmpty()) {
            return true;
        }
        doAssignments(pathMatchingResult.capturedVariables(), currentRequestRbelMessage, context);

        return combinedRequestCriterions.stream()
                .allMatch(criterion -> TigerJexlExecutor.matchesAsJexlExpression(
                        TigerGlobalConfiguration.resolvePlaceholdersWithContext(criterion, context), context))
                && pathMatchingResult.doesItMatch();
    }

    private void executeBackendRequestsBeforeDecision(TigerMockResponse mockResponse, TigerJexlContext jexlContext) {
        if (mockResponse.getBackendRequests() == null) {
            return;
        }

        for (ZionBackendRequestDescription requestDescription : mockResponse.getBackendRequests().values()) {
            try {
                var unirestResponse = prepareAndExecuteBackendRequest(requestDescription);

                final RbelElement rbelResponse = rbelLogger.getRbelConverter().convertElement(responseToRawMessage(unirestResponse), null);
                doAssignments(requestDescription.getAssignments(), rbelResponse, jexlContext);
            } catch (RuntimeException e) {
                log.error("Error during backend request '" + requestDescription.getMethod() + " " + requestDescription.getUrl() + "'", e);
                throw e;
            }
        }
    }

    private HttpResponse<byte[]> prepareAndExecuteBackendRequest(ZionBackendRequestDescription requestDescription) {
        final String method = getMethod(requestDescription);
        final HttpRequestWithBody unirestRequest = Unirest.request(
            method,
            TigerGlobalConfiguration.resolvePlaceholdersWithContext(
                requestDescription.getUrl(),
                new TigerJexlContext().withRootElement(this.requestRbelMessage)));
        if (requestDescription.getHeaders() != null) {
            requestDescription.getHeaders()
                .forEach(unirestRequest::header);
        }
        final HttpResponse<byte[]> unirestResponse;
        if (StringUtils.isNotEmpty(requestDescription.getBody())) {
            final RbelSerializationResult body = getBody(requestDescription);
            if (log.isTraceEnabled()) {
                log.trace("About to sent {} with body {} to {}",
                    unirestRequest.getHttpMethod().name(), new String(body.getContent()),
                    unirestRequest.getUrl());
            }
            unirestResponse = unirestRequest
                .body(body.getContent())
                .asBytes();
        } else {
            if (log.isTraceEnabled()) {
                log.trace("About to sent {} without body to {}",
                    unirestRequest.getHttpMethod().name(), unirestRequest.getUrl());
            }
            unirestResponse = unirestRequest.asBytes();
        }
        return unirestResponse;
    }

    private RbelSerializationResult getBody(ZionBackendRequestDescription requestDescription) {
        final String rawContent = TigerGlobalConfiguration.resolvePlaceholders(requestDescription.getBody());
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

    private byte[] responseToRawMessage(HttpResponse<byte[]> response) {
        byte[] httpResponseHeader = ("HTTP/1.1 " + response.getStatus() + " "
                                     + (response.getStatusText() != null ? response.getStatusText() : "") + "\r\n"
                                     + formatHeaderList(response.getHeaders())
                                     + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);

        return ArrayUtils.addAll(httpResponseHeader, response.getBody());
    }

    private String formatHeaderList(Headers headerList) {
        return headerList.all().stream()
            .map(h -> h.getName() + ": " + h.getValue())
            .collect(Collectors.joining("\r\n"));
    }

    @SneakyThrows
    private Optional<ResponseEntity<byte[]>> spyWithRemoteServer(RequestEntity<byte[]> request) {
        if (configuration.getSpy() == null) {
            return Optional.empty();
        }
        final URI targetUri = new URIBuilder(configuration.getSpy().getUrl())
            .setPath(request.getUrl().getPath())
            .setQuery(request.getUrl().getQuery())
            .build();

        final String name = Optional.ofNullable(request.getMethod())
            .map(HttpMethod::name)
            .orElse("");
        final HttpRequestWithBody unirestRequest = Unirest
            .request(name, targetUri.toString())
            .headers(request.getHeaders().entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, header -> String.join(",", header.getValue()))));
        if (request.hasBody()) {
            unirestRequest.body(request.getBody());
        }
        final HttpResponse<byte[]> unirestResponse = unirestRequest.asBytes();
        final ResponseEntity<byte[]> responseEntity = parseResponseWithRbelLogger(ResponseEntity.status(unirestResponse.getStatus())
            .body(unirestResponse.getBody()));
        final RbelElement responseRbelMessage = rbelLogger.getMessageHistory().getLast();
        final TigerMockResponse mockResponse = TigerMockResponse.builder()
            .requestCriterions(List.of(
                "message.method == '" + name + "'",
                "message.url =$ '" + getUriEnding(targetUri) + "'"
            ))
            .response(TigerMockResponseDescription.builder()
                .body(responseRbelMessage.getFirst("body")
                    .map(bodyElement -> rbelWriter.serializeWithEnforcedContentType(bodyElement, RbelContentType.JSON, new TigerJexlContext()))
                    .map(RbelSerializationResult::getContentAsString)
                    .orElse(null))
                .build())
            .build();
        FileUtils.writeStringToFile(Path.of(configuration.getSpy().getProtocolToPath(), "spy_" + UUID.randomUUID() + ".yaml").toFile(),
            objectMapper.writeValueAsString(mockResponse), Charset.defaultCharset());

        return Optional.of(responseEntity);
    }

    private String getUriEnding(URI targetUri) {
        if (StringUtils.isEmpty(targetUri.getQuery())) {
            return targetUri.getPath();
        } else {
            return targetUri.getPath() + "?" + targetUri.getQuery();
        }
    }


    private ResponseEntity<byte[]> parseResponseWithRbelLogger(ResponseEntity<byte[]> el) {
        rbelLogger.getRbelConverter()
            .parseMessage(buildRawMessageApproximate(el), serverHostname, clientHostname, Optional.of(ZonedDateTime.now()));

        return el;
    }


    private byte[] buildRawMessageApproximate(ResponseEntity<byte[]> response) {
        String header = "HTTP/1.1 " + response.getStatusCode().value();
        if (!response.getHeaders().isEmpty()) {
            header += "\r\n" + response.getHeaders().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                    .map(v -> entry.getKey() + ": " + v))
                .collect(Collectors.joining("\r\n"));
        }
        header += "\r\n\r\n";
        if (response.hasBody()) {
            return ArrayUtils.addAll(header.getBytes(), response.getBody());
        } else {
            return header.getBytes();
        }
    }

    private Optional<RbelSerializationResult> renderResponseBody(TigerMockResponse response, TigerJexlContext context) {
        Optional<String> bodyBlueprint = Optional.ofNullable(response.getResponse().getBody())
            .filter(Objects::nonNull)
            .or(() -> Optional.ofNullable(response.getResponse().getBodyFile())
                .map(Path::of)
                .map(p -> {
                    try {
                        return Files.readString(p);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull));
        if (bodyBlueprint.isEmpty()) {
            return Optional.empty(); //NOSONAR
        }

        return Optional.ofNullable(rbelWriter.serialize(
            rbelLogger.getRbelConverter().convertElement(bodyBlueprint.get(), null),
            context));
    }
}
