package de.gematik.test.tiger.zion.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerScopedExecutor;
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
import org.apache.http.client.utils.URIBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
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
                .map(this::renderResponse)
                .map(this::parseResponseWithRbelLogger)
                .or(() -> spyWithRemoteServer(request))
                .orElseThrow(() -> {
                    log.warn("Could not match request \n{}", requestRbelMessage.printTreeStructure());
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No suitable return value found");
                }));
    }

    private Optional<TigerMockResponse> findResponseForGivenRequest(RbelElement requestRbelMessage) {
        return configuration.getMockResponses().values().stream()
            .sorted(Comparator.comparing(TigerMockResponse::getImportance).reversed())
            .peek(resp -> {
                if (resp.getResponse() != null) {
                    log.trace("Considering response {} {}", resp.getResponse().getStatusCode(), resp.getResponse().getBody());
                } else {
                    log.trace("Considering response without body, nested responses: {}", resp.getNestedResponses().keySet());
                }
            })
            .map(entry -> findMatchingResponse(entry, requestRbelMessage))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }


    private ResponseEntity<byte[]> renderResponse(TigerMockResponse response) {
        doAssignments(response.getAssignments(), requestRbelMessage);

        final BodyBuilder responseBuilder = ResponseEntity
            .status(response.getResponse().getStatusCode());

        response.getResponse().getHeaders()
            .forEach((key, value) -> responseBuilder.header(key, new String(rbelWriter.serialize(
                rbelLogger.getRbelConverter().convertElement(value, null),
                new TigerJexlContext().withRootElement(requestRbelMessage)))));

        return responseBuilder
            .body(renderResponseBody(response));
    }

    private void doAssignments(Map<String, String> assignments, RbelElement currentElement) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }

        for (Entry<String, String> entry : assignments.entrySet()) {
            currentElement.findElement(entry.getValue())
                .map(RbelElement::getRawStringContent)
                .map(TigerGlobalConfiguration::resolvePlaceholders)
                .or(() -> TigerJexlExecutor.evaluateJexlExpression(entry.getValue(), new TigerJexlContext().withRootElement(currentElement))
                    .map(Object::toString))
                .ifPresent(value ->
                    TigerGlobalConfiguration.putValue(
                        TigerGlobalConfiguration.resolvePlaceholders(entry.getKey()),
                        value));
        }
    }

    private Optional<TigerMockResponse> findMatchingResponse(TigerMockResponse mockResponse, RbelElement requestRbelMessage) {
        executeBackendRequestsBeforeDecision(mockResponse);
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
        return requestCriterions.stream()
            .filter(criterion -> !TigerJexlExecutor.matchesAsJexlExpression(
                TigerGlobalConfiguration.resolvePlaceholders(criterion), new TigerJexlContext()
                    .withCurrentElement(requestRbelMessage)
                    .withRootElement(requestRbelMessage)))
            .findAny().isEmpty();
    }

    private void executeBackendRequestsBeforeDecision(TigerMockResponse mockResponse) {
        if (mockResponse.getBackendRequests() == null) {
            return;
        }
        for (ZionBackendRequestDescription requestDescription : mockResponse.getBackendRequests().values()) {
            final String method = getMethod(requestDescription);
            final HttpRequestWithBody unirestRequest = Unirest.request(
                method,
                TigerGlobalConfiguration.resolvePlaceholders(requestDescription.getUrl()));
            if (requestDescription.getHeaders() != null) {
                requestDescription.getHeaders()
                    .forEach(unirestRequest::header);
            }
            final HttpResponse<byte[]> unirestResponse;
            if (StringUtils.isNotEmpty(requestDescription.getBody())) {
                final byte[] body = getBody(requestDescription);
                if (log.isTraceEnabled()) {
                    log.trace("About to sent {} with body {} to {}",
                        unirestRequest.getHttpMethod().name(), new String(body),
                        unirestRequest.getUrl());
                }
                unirestResponse = unirestRequest.body(body).asBytes();
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("About to sent {} without body to {}",
                        unirestRequest.getHttpMethod().name(), unirestRequest.getUrl());
                }
                unirestResponse = unirestRequest.asBytes();
            }

            final RbelElement rbelResponse = rbelLogger.getRbelConverter().convertElement(responseToRawMessage(unirestResponse), null);
            doAssignments(requestDescription.getAssignments(), rbelResponse);
        }
    }

    private byte[] getBody(ZionBackendRequestDescription requestDescription) {
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
                    .map(String::new)
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

    private byte[] renderResponseBody(TigerMockResponse response) {
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
            return null; //NOSONAR
        }

        return rbelWriter.serialize(
            rbelLogger.getRbelConverter().convertElement(bodyBlueprint.get(), null),
            new TigerJexlContext().withRootElement(requestRbelMessage));
    }
}
