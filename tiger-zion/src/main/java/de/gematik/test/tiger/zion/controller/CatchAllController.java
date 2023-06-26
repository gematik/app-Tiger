package de.gematik.test.tiger.zion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import de.gematik.test.tiger.zion.services.ZionRequestExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class CatchAllController implements WebMvcConfigurer {

    private final RbelLogger rbelLogger;
    private final RbelWriter rbelWriter;
    private final ZionConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final ServletWebServerApplicationContext webServerAppCtxt;

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
    public ResponseEntity<byte[]> masterResponder(RequestEntity<byte[]> request, HttpServletRequest servletRequest) {
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

        final ResponseEntity<byte[]> response = ZionRequestExecutor.builder()
            .clientHostname(client)
            .serverHostname(server)
            .requestRbelMessage(requestRbelMessage)
            .rbelLogger(rbelLogger)
            .rbelWriter(rbelWriter)
            .objectMapper(objectMapper)
            .localServerPort(webServerAppCtxt.getWebServer().getPort())
            .configuration(configuration)
            .request(request)
            .build().execute();

        log.info("Returning response {}", response);
        return response;
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
}
