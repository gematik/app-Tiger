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
package de.gematik.test.tiger.proxy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import io.restassured.RestAssured;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ResetTigerConfiguration
class ClockEndpointTest {

  @LocalServerPort private int port;

  @Test
  @DisplayName("GET /clock returns a serverTime close to now")
  void clockEndpoint_returnsCurrentTime() {
    ZonedDateTime before = ZonedDateTime.now();

    var response =
        RestAssured.given()
            .port(port)
            .get("/clock")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath();

    String serverTimeStr = response.getString("serverTime");
    assertThat(serverTimeStr).isNotNull();

    ZonedDateTime serverTime = ZonedDateTime.parse(serverTimeStr);
    ZonedDateTime after = ZonedDateTime.now();

    assertThat(serverTime)
        .as("Server time should be between the before and after timestamps")
        .isAfterOrEqualTo(before.minus(1, ChronoUnit.SECONDS))
        .isBeforeOrEqualTo(after.plus(1, ChronoUnit.SECONDS));
  }
}
