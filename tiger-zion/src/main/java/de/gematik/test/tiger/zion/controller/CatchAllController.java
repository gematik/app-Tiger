package de.gematik.test.tiger.zion.controller;

import static org.springframework.web.bind.annotation.RequestMethod.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.TigerMockResponseDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class CatchAllController implements WebMvcConfigurer {

    private final RbelLogger rbelLogger;
    private final RbelWriter rbelWriter;
    private final ZionConfiguration configuration;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @PostConstruct
    public void loadMockReponsesFromFile() {
        if (configuration.getMockResponseFiles() == null ||
            configuration.getMockResponseFiles().isEmpty()) {
            log.info("Skipping initialization for mock-responses from files, none specified");
            return;
        }
        for (Entry<String, String> entry : configuration.getMockResponseFiles().entrySet()) {
            final File file = Path.of(entry.getValue()).toFile();
            try (final FileInputStream fileInputStream = new FileInputStream(file)) {
                final TigerMockResponse mockResponse = new Yaml(new Constructor(TigerMockResponse.class)).load(fileInputStream);
                configuration.getMockResponses().put(entry.getKey(), mockResponse);
                log.info("Successfully added mock-response from file {} with criteria {}",
                    file.getAbsolutePath(),
                    String.join(", ", mockResponse.getRequestCriterions()));
            }
        }
    }

    @RequestMapping(value = "**",
        consumes = {"*/*", "application/*"}, produces = "*/*",
        method = {GET, POST, HEAD, OPTIONS, PUT, PATCH, DELETE, TRACE}) // NOSONAR
    public ResponseEntity<byte[]> getAnythingelse(RequestEntity<byte[]> request, HttpServletRequest servletRequest) {
        log.info("Got new request {} {}", request.getMethod(), request.getUrl());

        byte[] rawMessage = buildRawMessageApproximate(request);
        final RbelHostname client = RbelHostname.builder()
            .hostname(servletRequest.getRemoteHost())
            .port(servletRequest.getRemotePort())
            .build();
        final RbelHostname server = RbelHostname.builder()
            .hostname(servletRequest.getLocalAddr())
            .port(servletRequest.getLocalPort())
            .build();
        final RbelElement requestRbelMessage = rbelLogger.getRbelConverter()
            .parseMessage(rawMessage, client, server, Optional.of(ZonedDateTime.now()));

        TigerJexlExecutor.ELEMENT_STACK.push(requestRbelMessage);
        final ResponseEntity responseEntity = configuration.getMockResponses().values().stream()
            .filter(entry -> doesItMatch(entry.getRequestCriterions(), requestRbelMessage))
            .findAny()
            .map(this::renderResponse)
            .map(el -> parseResponseWithRbelLogger(client, server, el))
            .or(() -> spyWithRemoteServer(request, client, server))
            .orElseThrow(() -> {
                log.warn("Could not match request \n{}", requestRbelMessage.printTreeStructure());
                return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No suitable return value found");
            });

        TigerJexlExecutor.ELEMENT_STACK.removeFirstOccurrence(requestRbelMessage);
        return responseEntity;
    }

    @SneakyThrows
    private Optional<ResponseEntity<byte[]>> spyWithRemoteServer(RequestEntity<byte[]> request, RbelHostname client, RbelHostname server) {
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
        final ResponseEntity<byte[]> responseEntity = parseResponseWithRbelLogger(client, server, ResponseEntity.status(unirestResponse.getStatus())
            .body(unirestResponse.getBody()));
        final RbelElement responseRbelMessage = rbelLogger.getMessageHistory().getLast();
        final TigerMockResponse mockResponse = TigerMockResponse.builder()
            .requestCriterions(List.of(
                "message.method == '" + name + "'",
                "message.url =$ '" + getUriEnding(targetUri) + "'"
            ))
            .response(TigerMockResponseDescription.builder()
                .body(responseRbelMessage.getFirst("body")
                    .map(bodyElement -> rbelWriter.serializeWithEnforcedContentType(bodyElement, RbelContentType.JSON))
                    .map(String::new)
                    .orElse(null))
                .build())
            .build();
        FileUtils.writeStringToFile(Path.of(configuration.getSpy().getProtocolToPath(), "spy_" + UUID.randomUUID() + ".yaml").toFile(),
            objectMapper.writeValueAsString(mockResponse));

        return Optional.of(responseEntity);
    }

    private String getUriEnding(URI targetUri) {
        if (StringUtils.isEmpty(targetUri.getQuery())) {
            return targetUri.getPath();
        } else {
            return targetUri.getPath() + "?" + targetUri.getQuery();
        }
    }

    private ResponseEntity<byte[]> parseResponseWithRbelLogger(RbelHostname client, RbelHostname server, ResponseEntity<byte[]> el) {
        try {
            rbelLogger.getRbelConverter()
                .parseMessage(buildRawMessageApproximate(el), server, client, Optional.of(ZonedDateTime.now()));

            var rbelRenderer = new RbelHtmlRenderer();
            FileUtils.writeStringToFile(new File("target/traffic.html"), rbelRenderer.doRender(rbelLogger.getMessageList()));
            return el;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<byte[]> renderResponse(TigerMockResponse response) {
        final BodyBuilder responseBuilder = ResponseEntity
            .status(response.getResponse().getStatusCode());

        response.getResponse().getHeaders()
            .forEach((key, value) -> responseBuilder.header(key, new String(rbelWriter.serialize(
                rbelLogger.getRbelConverter().convertElement(value, null)))));

        return responseBuilder
            .body(renderResponseBody(response));
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

        final byte[] renderedBody = rbelWriter.serialize(
            rbelLogger.getRbelConverter().convertElement(bodyBlueprint.get(), null));
        log.info("Returning {}", new String(renderedBody));
        return renderedBody;
    }

    private boolean doesItMatch(List<String> requestCriterions, RbelElement requestRbelMessage) {
        return requestCriterions.stream()
            .filter(criterion -> !TigerJexlExecutor.INSTANCE.matchesAsJexlExpression(requestRbelMessage, criterion))
            .findAny().isEmpty();
    }

    private byte[] buildRawMessageApproximate(RequestEntity<byte[]> request) {
        final String header = request.getMethod() + " " + request.getUrl() + " HTTP/1.1\r\n" + // NOSONAR
            request.getHeaders().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                    .map(v -> entry.getKey() + ": " + v))
                .collect(Collectors.joining("\r\n"))
            + "\r\n\r\n";
        if (request.hasBody()) {
            return ArrayUtils.addAll(header.getBytes(), request.getBody());
        } else {
            return header.getBytes();
        }
    }

    private byte[] buildRawMessageApproximate(ResponseEntity<byte[]> response) {
        String header = "HTTP/1.1 " + response.getStatusCodeValue();
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
}