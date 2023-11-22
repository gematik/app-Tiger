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
import kong.unirest.Unirest;
import lombok.SneakyThrows;
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
class TestNestedResponses {

  final Path tempDirectory = Path.of("target", "zionResponses");

  @Autowired private ZionConfiguration configuration;
  @LocalServerPort private int port;
  private Map<String, TigerMockResponse> mockResponsesBackup;

  @SneakyThrows
  @BeforeEach
  public void setupTempDirectory() {
    Files.createDirectories(tempDirectory);
    Files.list(tempDirectory).forEach(path -> path.toFile().delete());
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
                                TigerMockResponseDescription.builder().statusCode(405).build())
                            .importance(0)
                            .build(),
                        "rightPassword",
                        TigerMockResponse.builder()
                            .requestCriterions(
                                List.of("$.header.password == '" + passwordString + "'"))
                            .response(
                                TigerMockResponseDescription.builder().statusCode(200).build())
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
