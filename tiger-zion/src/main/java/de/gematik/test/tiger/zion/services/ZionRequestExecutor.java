package de.gematik.test.tiger.zion.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelSerializationResult;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerScopedExecutor;
import de.gematik.test.tiger.common.config.TigerScopedExecutorWithVariable;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.TigerMockResponseDescription;
import de.gematik.test.tiger.zion.config.ZionBackendRequestDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
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
import kong.unirest.*;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.server.ResponseStatusException;

@Builder
@Slf4j
public class ZionRequestExecutor {

    @NonNull
    private final RbelElement requestRbelMessage;
    @NonNull
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
    private TigerScopedExecutor tigerScopedExecutor;

    public ResponseEntity<byte[]> execute() {
        tigerScopedExecutor = TigerGlobalConfiguration.localScope()
            .withValue("zion.port", String.valueOf(localServerPort));
        return tigerScopedExecutor
            .retrieve(() -> findResponseForGivenRequest(requestRbelMessage)
                .map(executorWithResponse -> executorWithResponse.mergeWith(response -> doAssignments(response.getAssignments(), requestRbelMessage)))
                .map(executorWithVariable -> executorWithVariable.retrieve(this::renderResponse).getVariable())
                .map(this::parseResponseWithRbelLogger)
                .or(() -> spyWithRemoteServer(request))
                .orElseThrow(() -> {
                    log.warn("Could not match request \n{}", requestRbelMessage.printTreeStructure());
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No suitable return value found");
                }));
    }

    private Optional<TigerScopedExecutorWithVariable<TigerMockResponse>> findResponseForGivenRequest(RbelElement requestRbelMessage) {
        for (TigerMockResponse response : configuration.getMockResponses().values().stream()
            .sorted(Comparator.comparing(TigerMockResponse::getImportance).reversed())
            .toList()) {
            if (response.getResponse() != null) {
                log.trace("Considering response {} {}", response.getResponse().getStatusCode(), response.getResponse().getBody());
            } else {
                log.trace("Considering response without body, nested responses: {}", response.getNestedResponses().keySet());
            }
            final TigerScopedExecutor scopedExecutor = executeBackendRequestsBeforeDecision(response, doAssignments(response.getAssignments(), requestRbelMessage));
            final Optional<TigerMockResponse> responseCandidate = scopedExecutor.retrieve(() -> findMatchingResponse(response, requestRbelMessage));
            // responseCandidate is not necessarily equal to response: nestedResponses!
            if (responseCandidate.isPresent()) {
                return Optional.of(new TigerScopedExecutorWithVariable<>(responseCandidate.get(), scopedExecutor));
            }
        }
        return Optional.empty();
    }

    private ResponseEntity<byte[]> renderResponse(TigerMockResponse response) {
        final Optional<RbelSerializationResult> serializationResult = renderResponseBody(response);

        final BodyBuilder responseBuilder = ResponseEntity
            .status(response.getResponse().getStatusCode());
        serializationResult.flatMap(RbelSerializationResult::getContentType)
            .map(RbelContentType::getContentTypeString)
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

    private TigerScopedExecutor doAssignments(Map<String, String> assignments, RbelElement currentElement) {
        return doAssignments(assignments, currentElement, new TigerScopedExecutor());
    }

    private TigerScopedExecutor doAssignments(Map<String, String> assignments, RbelElement currentElement, TigerScopedExecutor scopedExecutor) {
        if (assignments == null || assignments.isEmpty()) {
            return new TigerScopedExecutor();
        }

        scopedExecutor.execute(() -> {
            for (Entry<String, String> entry : assignments.entrySet()) {
                final Optional<String> potentialValue = currentElement.findElement(entry.getValue())
                    .map(RbelElement::getRawStringContent)
                    .map(TigerGlobalConfiguration::resolvePlaceholders)
                    .or(() -> TigerJexlExecutor.evaluateJexlExpression(entry.getValue(), new TigerJexlContext().withRootElement(currentElement))
                        .map(Object::toString));
                if (potentialValue.isPresent()) {
                    final String key = TigerGlobalConfiguration.resolvePlaceholders(entry.getKey());
                    scopedExecutor.withValue(key, potentialValue.get());
                }
            }
        });
        return scopedExecutor;
    }

    private Optional<TigerMockResponse> findMatchingResponse(TigerMockResponse mockResponse, RbelElement requestRbelMessage) {
        if (!doesItMatch(mockResponse.getRequestCriterions(), requestRbelMessage)) {
            if (log.isTraceEnabled() && (mockResponse.getResponse() != null)) {
                log.trace("Discarding response {} {} with criterions {} for message {}",
                    mockResponse.getResponse().getStatusCode(), mockResponse.getResponse().getBody(),
                    mockResponse.getRequestCriterions(), requestRbelMessage.printTreeStructureWithoutColors());
            }
            return Optional.empty();
        }
        if (mockResponse.getResponse() != null) {
            log.trace("Considering response {} {}", mockResponse.getResponse().getStatusCode(), mockResponse.getResponse().getBody());
            return Optional.of(mockResponse);
        } else {
            return Optional.ofNullable(mockResponse.getNestedResponses())
                .map(Map::values)
                .stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(TigerMockResponse::getImportance).reversed())
                .map(r -> findMatchingResponse(r, requestRbelMessage))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .peek(resp -> log.trace("Considering response {} {}", resp.getResponse().getStatusCode(), resp.getResponse().getBody()))
                .findFirst();
        }
    }

    private boolean doesItMatch(List<String> requestCriterions, RbelElement requestRbelMessage) {
        if (requestCriterions == null) {
            return true;
        }
        final TigerJexlContext context = new TigerJexlContext()
            .withCurrentElement(requestRbelMessage)
            .withRootElement(requestRbelMessage);
        return requestCriterions.stream()
            .filter(criterion -> !TigerJexlExecutor.matchesAsJexlExpression(
                TigerGlobalConfiguration.resolvePlaceholdersWithContext(criterion, context), context))
            .findAny().isEmpty();
    }

    private TigerScopedExecutor executeBackendRequestsBeforeDecision(TigerMockResponse mockResponse, TigerScopedExecutor scopedExecutor) {
        if (mockResponse.getBackendRequests() == null) {
            return scopedExecutor;
        }
        scopedExecutor.execute(() -> {
            for (ZionBackendRequestDescription requestDescription : mockResponse.getBackendRequests().values()) {
                HttpResponse<byte[]> unirestResponse = null;
                try {
                    unirestResponse = prepareAndExecuteBackendRequest(requestDescription);
                } catch (RuntimeException e) {
                    log.error("Error during backend request", e);
                    throw e;
                }

                final RbelElement rbelResponse = rbelLogger.getRbelConverter().convertElement(responseToRawMessage(unirestResponse), null);
                doAssignments(requestDescription.getAssignments(), rbelResponse, scopedExecutor);
            }
        });
        return scopedExecutor;
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
                .collect(Collectors.toMap(Entry::getKey, header -> header.getValue().stream().collect(Collectors.joining(",")))));
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

    private Optional<RbelSerializationResult> renderResponseBody(TigerMockResponse response) {
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
            new TigerJexlContext().withRootElement(requestRbelMessage)));
    }
}
