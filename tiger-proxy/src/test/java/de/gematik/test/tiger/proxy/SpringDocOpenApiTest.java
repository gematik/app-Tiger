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
 */
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import kong.unirest.core.json.JSONObject;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
@ResetTigerConfiguration
@DirtiesContext
class SpringDocOpenApiTest {

  @LocalServerPort private int port;

  private UnirestInstance unirest;

  @BeforeEach
  void setUp() {
    unirest = Unirest.spawnInstance();
    unirest.config().defaultBaseUrl("http://localhost:" + port);
  }

  @Test
  void swaggerUiHtml_shouldBeServed() {
    HttpResponse<String> response = unirest.get("/swagger-ui/index.html").asString();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody()).contains("Swagger UI");
  }

  @Test
  void openApiJson_shouldBeAvailable() {
    HttpResponse<String> response = unirest.get("/v3/api-docs").asString();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeaders().getFirst("Content-Type")).contains("application/json");
  }

  @Test
  void openApiJson_shouldContainProxyControllerEndpoints() {
    JSONObject apiDocs = unirest.get("/v3/api-docs").asJson().getBody().getObject();

    JSONObject paths = apiDocs.getJSONObject("paths");

    // TigerConfigurationController
    assertThat(paths.has("/route")).isTrue();

    // TigerModificationController
    assertThat(paths.has("/modification")).isTrue();

    // TracingpointsController
    assertThat(paths.has("/tracingpoints")).isTrue();
  }

  @Test
  void openApiJson_shouldBeValidOpenApi3Document() {
    JSONObject apiDocs = unirest.get("/v3/api-docs").asJson().getBody().getObject();

    assertThat(apiDocs.getString("openapi")).startsWith("3.");
    assertThat(apiDocs.has("info")).isTrue();
    assertThat(apiDocs.has("paths")).isTrue();
  }

  @Test
  void openApiYaml_shouldBeAvailable() {
    HttpResponse<String> response = unirest.get("/v3/api-docs.yaml").asString();

    assertThat(response.getStatus()).isEqualTo(200);
    // YAML uses a different content-type than JSON (/v3/api-docs returns application/json)
    assertThat(response.getHeaders().getFirst("Content-Type"))
        .contains("application/vnd.oai.openapi");
  }
}
