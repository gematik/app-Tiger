/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 */
package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.TigerMockResponseDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kong.unirest.core.Unirest;
import lombok.SneakyThrows;
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
@ResetTigerConfiguration
class TestMeshedSetups {

  final Path tempDirectory = Path.of("target", "zionResponses");

  @Autowired private ZionConfiguration configuration;
  @LocalServerPort private int port;
  private Map<String, TigerMockResponse> mockResponsesBackup;

  @SneakyThrows
  @BeforeEach
  public void setupTempDirectory() {
    Files.createDirectories(tempDirectory);
    FileUtils.deleteDirectory(tempDirectory.toFile());
    mockResponsesBackup = configuration.getMockResponses();
  }

  @AfterEach
  public void resetMockResponses() {
    configuration.setMockResponses(mockResponsesBackup);
    configuration.setSpy(null);
  }

  @Test
  void simpleMockedResponse() {
    final String passwordString = "123secret";
    configuration.setMockResponses(
        Map.of(
            "passwordRequired",
            TigerMockResponse.builder()
                .requestCriterions(List.of("message.method == 'GET'", "message.path =~ '/secret'"))
                .nestedResponses(
                    Map.of(
                        "wrongPassword",
                        TigerMockResponse.builder()
                            .response(
                                TigerMockResponseDescription.builder().statusCode("405").build())
                            .importance(0)
                            .build(),
                        "rightPassword",
                        TigerMockResponse.builder()
                            .requestCriterions(
                                List.of("$.header.password == '" + passwordString + "'"))
                            .response(
                                TigerMockResponseDescription.builder().statusCode("200").build())
                            .importance(10)
                            .build()))
                .build()));

    assertThat(Unirest.get("http://localhost:" + port + "/secret").asEmpty().getStatus())
        .isEqualTo(405);
    assertThat(
            Unirest.get("http://localhost:" + port + "/secret")
                .header("password", "wrongPassword")
                .asEmpty()
                .getStatus())
        .isEqualTo(405);
    assertThat(
            Unirest.get("http://localhost:" + port + "/secret")
                .header("password", passwordString)
                .asEmpty()
                .getStatus())
        .isEqualTo(200);
  }
}
