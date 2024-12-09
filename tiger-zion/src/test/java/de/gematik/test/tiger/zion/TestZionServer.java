/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.zion;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.TigerMockResponseDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TestZionServer {

  final Path tempDirectory = Path.of("target", "zionResponses");

  @Autowired private ZionConfiguration configuration;
  @LocalServerPort private int port;

  @SneakyThrows
  @BeforeEach
  public void setupTempDirectory() {
    Files.createDirectories(tempDirectory);
    Files.list(tempDirectory).forEach(path -> path.toFile().delete());
  }

  @AfterEach
  public void resetMockResponses() {
    configuration.setMockResponses(new HashMap<>());
    configuration.setSpy(null);
  }

  @SneakyThrows
  @Test
  void binaryContent_shouldWork() {
    final byte[] data = DigestUtils.sha256("Hello World".getBytes());
    final String filename = "target/blob.bin";
    FileUtils.writeByteArrayToFile(new File(filename), data);

    configuration.setMockResponses(
        Map.of(
            "test",
            TigerMockResponse.builder()
                .nestedResponses(
                    Map.of(
                        "regularResponse",
                        TigerMockResponse.builder()
                            .response(
                                TigerMockResponseDescription.builder().bodyFile(filename).build())
                            .build()))
                .build()));

    final HttpResponse<byte[]> bytes = Unirest.post("http://localhost:" + port + "/test").asBytes();

    assertThat(bytes.getBody()).isEqualTo(data);
  }

  @SneakyThrows
  @Test
  void zipEncodedContent_shouldWork() {
    final String body = "{\"foo\": \"bar\"}";
    configuration.setMockResponses(
        Map.of(
            "test",
            TigerMockResponse.builder()
                .nestedResponses(
                    Map.of(
                        "regularResponse",
                        TigerMockResponse.builder()
                            .response(
                                TigerMockResponseDescription.builder()
                                    .body(body)
                                    .encoding("gzip")
                                    .build())
                            .build()))
                .build()));

    final int proxyPort =
        TigerGlobalConfiguration.readIntegerOptional("free.port.123").orElseThrow();
    try (var tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyPort(proxyPort)
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("/")
                            .to("http://localhost:" + port + "/")
                            .build()))
                .build())) {

      final HttpResponse<String> bytes =
          Unirest.post("http://localhost:" + proxyPort + "/test").asString();
      await().until(() -> tigerProxy.getRbelMessagesList().size() == 2);

      assertThat(tigerProxy.getRbelMessagesList().get(1))
          .extractChildWithPath("$.header.[~'content-encoding']")
          .hasStringContentEqualTo("gzip");

      assertThat(bytes.getBody()).isEqualTo(body);
    }
  }
}
