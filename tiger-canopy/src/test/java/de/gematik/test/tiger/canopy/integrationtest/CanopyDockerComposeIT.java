/*
 *
 * Copyright 2021-2026 gematik GmbH
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
 *
 */
package de.gematik.test.tiger.canopy.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

/**
 * End-to-end Testcontainers smoke test for the CANOPY Docker image: builds the image from the
 * checked-in {@code tiger-canopy/Dockerfile}, boots the container, and verifies that a registered
 * proxied host resolves to the configured Tiger-proxy IP via the running DNS server.
 *
 * <p>Skipped automatically when no Docker daemon is reachable, so it is safe to leave on the
 * default failsafe path.
 */
@Slf4j
@SuppressWarnings("resource") // container lifecycle is managed via @BeforeAll/@AfterAll
class CanopyDockerComposeIT {

  /** Resolution target — host part of {@code tigerProxyUrl}; resolves to {@code 127.0.0.1}. */
  private static final String PROXY_IP_LITERAL = "127.0.0.1";

  private static GenericContainer<?> canopy;

  @BeforeAll
  static void start() {
    Assumptions.assumeThat(DockerClientFactory.instance().isDockerAvailable())
        .as("Docker is required for CanopyDockerComposeIT")
        .isTrue();

    Path moduleDir = findModuleDir();
    Path dockerfile = moduleDir.resolve("Dockerfile");
    Path target = moduleDir.resolve("target");
    log.info("Using tiger-canopy module dir: {}", moduleDir);

    if (!Files.exists(dockerfile)) {
      throw new IllegalStateException(
          "Dockerfile not found at "
              + dockerfile
              + ". Module-dir auto-detect failed; cwd="
              + Paths.get("").toAbsolutePath());
    }
    Path execJar = findExecJar(target);
    if (execJar == null) {
      throw new IllegalStateException(
          "tiger-canopy-*-exec.jar not found in "
              + target
              + ". Run 'mvn -pl tiger-canopy package -DskipTests' (or 'mvn -pl tiger-canopy"
              + " -am verify') before running this IT from the IDE.");
    }
    log.info(
        "Building image from Dockerfile={} with exec jar={}", dockerfile, execJar.getFileName());

    ImageFromDockerfile image =
        new ImageFromDockerfile("tiger-canopy-it", true)
            .withFileFromPath("Dockerfile", dockerfile)
            .withFileFromPath("target", target);

    canopy =
        new GenericContainer<>(image)
            .withExposedPorts(53, 8080)
            .withEnv(
                "SPRING_APPLICATION_JSON",
                """
                {
                  "canopy": {
                    "dnsPort": 53,
                    "tigerProxyUrl": "http://%s:9090/",
                    "controlMode": "NONE",
                    "proxiedHosts": [
                      {"host": "api.example.com", "matchType": "EXACT"}
                    ]
                  }
                }"""
                    .formatted(PROXY_IP_LITERAL))
            .waitingFor(
                Wait.forHttp("/actuator/health")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(60)));
    canopy.start();
  }

  @AfterAll
  static void stop() {
    if (canopy != null) {
      canopy.stop();
    }
  }

  /**
   * Locates the {@code tiger-canopy} module directory regardless of whether the test runner's
   * working directory is the module itself (Maven failsafe) or the parent project root (typical
   * IntelliJ default).
   */
  private static Path findModuleDir() {
    Path cwd = Paths.get("").toAbsolutePath();
    if (Files.exists(cwd.resolve("Dockerfile"))
        && Files.isDirectory(cwd.resolve("src/main/java/de/gematik/test/tiger/canopy"))) {
      return cwd;
    }
    Path p = cwd;
    while (p != null) {
      Path candidate = p.resolve("tiger-canopy");
      if (Files.exists(candidate.resolve("Dockerfile"))) {
        return candidate;
      }
      p = p.getParent();
    }
    return cwd; // assumption will fail with a useful path in the message
  }

  private static Path findExecJar(Path target) {
    if (!Files.isDirectory(target)) {
      return null;
    }
    try (Stream<Path> jars = Files.list(target)) {
      return jars.filter(
              p -> {
                String n = p.getFileName().toString();
                return n.startsWith("tiger-canopy-") && n.endsWith("-exec.jar");
              })
          .max(Comparator.comparing(Path::getFileName))
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  @Test
  void registeredHost_resolvesToConfiguredProxyIp() throws Exception {
    String dnsHost = canopy.getHost();
    int dnsPort = canopy.getMappedPort(53);
    log.info("Querying CANOPY DNS at {}:{} (TCP)", dnsHost, dnsPort);

    SimpleResolver resolver = new SimpleResolver(new InetSocketAddress(dnsHost, dnsPort));
    resolver.setTCP(true); // bypass UDP — Testcontainers maps TCP only.
    resolver.setTimeout(Duration.ofSeconds(5));

    Record question = Record.newRecord(Name.fromString("api.example.com."), Type.A, DClass.IN);
    Message response = resolver.send(Message.newQuery(question));

    assertThat(response.getSection(Section.ANSWER))
        .as("answer section for api.example.com")
        .isNotEmpty()
        .anySatisfy(
            r -> {
              assertThat(r).isInstanceOf(ARecord.class);
              assertThat(((ARecord) r).getAddress().getHostAddress()).isEqualTo(PROXY_IP_LITERAL);
            });
  }

  @Test
  void restApi_listsConfiguredHost() throws Exception {
    URI uri =
        URI.create(
            "http://"
                + canopy.getHost()
                + ":"
                + canopy.getMappedPort(8080)
                + "/api/v1/proxied-hosts");
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("api.example.com");
  }
}
