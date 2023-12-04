package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.TigerMockResponseDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition;
import java.util.List;
import java.util.Map;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
class TestAssignments {

  @Autowired private ZionConfiguration configuration;
  @LocalServerPort private int port;
  private Map<String, TigerMockResponse> mockResponsesBackup;

  @SneakyThrows
  @BeforeEach
  public void setupTempDirectory() {
    TigerGlobalConfiguration.reset();
    mockResponsesBackup = configuration.getMockResponses();
  }

  @AfterEach
  public void resetMockResponses() {
    TigerGlobalConfiguration.reset();
    configuration.setMockResponses(mockResponsesBackup);
    configuration.setSpy(null);
  }

  @ParameterizedTest
  @CsvSource({"$.method,GET", "!{$.method},GET", "?{$.method},GET"})
  void testAssignments(String extractorString, String expectedValue) {
    configuration.setMockResponses(
        Map.of(
            "backend_foobar",
            TigerMockResponse.builder()
                .requestCriterions(List.of()) // always true
                .assignments(Map.of("theAssignedValue", extractorString))
                .response(
                    TigerMockResponseDescription.builder()
                        .statusCode(666)
                        .body("${theAssignedValue}")
                        .build())
                .build()));

    final HttpResponse<String> response =
        Unirest.get("http://localhost:" + port + "/userJsonPath?username=someUsername").asString();
    assertThat(response.getBody()).isEqualTo(expectedValue);
  }

  @Test
  void testAssignmentInNestedResponse() {
    configuration.setMockResponses(
        Map.of(
            "level1",
            TigerMockResponse.builder()
                .assignments(Map.of("level1Assignment", "level1Value"))
                .nestedResponses(
                    Map.of(
                        "level2",
                        TigerMockResponse.builder()
                            .assignments(Map.of("level2Assignment", "level2Value"))
                            .response(
                                TigerMockResponseDescription.builder()
                                    .statusCode(666)
                                    .body("${level1Assignment} + ${level2Assignment}")
                                    .build())
                            .build()))
                .build()));

    HttpResponse<String> response = Unirest.get("http://localhost:" + port).asString();
    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody()).isEqualTo("level1Value + level2Value");
  }

  @Test
  void testAssignmentInNextResponse() {
    configuration.setMockResponses(
        Map.of(
            "retrieveValue",
            TigerMockResponse.builder()
                .request(ZionRequestMatchDefinition.builder().method("GET").build())
                .response(TigerMockResponseDescription.builder().body("${myStoredValue}").build())
                .build(),
            "putValue",
            TigerMockResponse.builder()
                .request(ZionRequestMatchDefinition.builder().method("PUT").build())
                .assignments(Map.of("myStoredValue", "!{$.body}"))
                .response(TigerMockResponseDescription.builder().statusCode(200).build())
                .build()));

    Unirest.put("http://localhost:" + port).body("foobar").asEmpty();
    HttpResponse<String> response = Unirest.get("http://localhost:" + port).asString();
    assertThat(response.getBody()).isEqualTo("foobar");
  }

  @Test
  void testAssignmentsWithFallBacks() {
    configuration.setMockResponses(
        Map.of(
            "level1",
            TigerMockResponse.builder()
                .assignments(Map.of("level1Assignment", "level1Value"))
                .nestedResponses(
                    Map.of(
                        "level2",
                        TigerMockResponse.builder()
                            .response(
                                TigerMockResponseDescription.builder()
                                    .statusCode(666)
                                    .body(
                                        "${level1Assignment|fallback1} + ${level2AssignmentNotReallyExisting|fallback2}")
                                    .build())
                            .build()))
                .build()));

    HttpResponse<String> response = Unirest.get("http://localhost:" + port).asString();
    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody()).isEqualTo("level1Value + fallback2");
  }
}
