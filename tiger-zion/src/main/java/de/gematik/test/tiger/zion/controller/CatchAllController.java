package de.gematik.test.tiger.zion.controller;

import static org.springframework.web.bind.annotation.RequestMethod.*;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelFileWriter;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class CatchAllController implements WebMvcConfigurer {

    private final RbelLogger rbelLogger;
    private final RbelWriter rbelWriter;
    private final ZionConfiguration configuration;

    @RequestMapping(value = "**",
        consumes = {"*/*", "application/*"}, produces = "*/*",
        method = {GET, POST, HEAD, OPTIONS, PUT, PATCH, DELETE, TRACE}) // NOSONAR
    public ResponseEntity<byte[]> getAnythingelse(RequestEntity<byte[]> request, HttpServletRequest servletRequest) throws IOException {
        log.info("Got new request {} {}", request.getMethod(), request.getUrl()); // NOSONAR

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
        final TigerMockResponse response = configuration.getMockResponses().values().stream()
            .filter(entry -> doesItMatch(entry.getRequestCriterions(), requestRbelMessage))
            .findAny()
            .orElseThrow(() -> {
                log.warn("Could not match request \n{}", requestRbelMessage.printTreeStructure());
                return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No suitable return value found");
            });

        final ResponseEntity<byte[]> responseEntity = renderResponse(response);
        rbelLogger.getRbelConverter()
            .parseMessage(buildRawMessageApproximate(responseEntity), server, client, Optional.of(ZonedDateTime.now()));

        var rbelRenderer = new RbelHtmlRenderer();
        FileUtils.writeStringToFile(new File("target/traffic.html"), rbelRenderer.doRender(rbelLogger.getMessageList()));

        TigerJexlExecutor.ELEMENT_STACK.removeFirstOccurrence(requestRbelMessage);
        return responseEntity;
    }

    private ResponseEntity<byte[]> renderResponse(TigerMockResponse response) {
        final BodyBuilder responseBuilder = ResponseEntity
            .status(response.getResponse().getStatusCode());

        response.getResponse().getHeader()
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
        final String header = "HTTP/1.1 " + response.getStatusCodeValue() + " \r\n" +
            response.getHeaders().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                    .map(v -> entry.getKey() + ": " + v))
                .collect(Collectors.joining("\r\n"))
            + "\r\n\r\n";
        if (response.hasBody()) {
            return ArrayUtils.addAll(header.getBytes(), response.getBody());
        } else {
            return header.getBytes();
        }
    }
}