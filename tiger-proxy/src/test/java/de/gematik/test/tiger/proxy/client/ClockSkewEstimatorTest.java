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
package de.gematik.test.tiger.proxy.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import de.gematik.test.tiger.proxy.controller.TracingpointsController;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class ClockSkewEstimatorTest {

  private static final String CLOCK_TRANSFORMER = "dynamic-clock";
  private static final String PARAM_SKEW_MILLIS = "skewMillis";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().extensions(new DynamicClockTransformer()))
          .build();

  /** Stubs {@code GET /clock} so each request returns a fresh "now + skew" timestamp. */
  private static void stubDynamicClock(Duration skew) {
    wm.stubFor(
        get("/clock")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withTransformers(CLOCK_TRANSFORMER)
                    .withTransformerParameter(PARAM_SKEW_MILLIS, skew.toMillis())));
  }

  @Test
  @DisplayName("Zero samples returns Optional.empty()")
  void zeroSamples_returnsEmpty() {
    assertThat(ClockSkewEstimator.estimateOffset("http://localhost:1", 0)).isEmpty();
  }

  @Test
  @DisplayName("Negative samples returns Optional.empty()")
  void negativeSamples_returnsEmpty() {
    assertThat(ClockSkewEstimator.estimateOffset("http://localhost:1", -1)).isEmpty();
  }

  @Test
  @DisplayName("Estimator returns near-zero offset when clocks are the same (localhost)")
  void sameHost_returnsSmallOffset() {
    stubDynamicClock(Duration.ZERO);

    Optional<Duration> offset = ClockSkewEstimator.estimateOffset(wm.baseUrl(), 3);

    assertThat(offset).isPresent();
    assertThat(offset.get().abs()).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  @DisplayName("Estimator returns Optional.empty() when all requests fail")
  void allRequestsFail_returnsEmpty() {
    assertThat(ClockSkewEstimator.estimateOffset("http://localhost:1", 3)).isEmpty();
  }

  @Test
  @DisplayName("Estimator detects a simulated clock offset")
  void simulatedOffset_isDetected() {
    Duration simulatedSkew = Duration.ofSeconds(10);
    stubDynamicClock(simulatedSkew);

    Optional<Duration> offset = ClockSkewEstimator.estimateOffset(wm.baseUrl(), 3);

    assertThat(offset).isPresent();
    assertThat(offset.get().toMillis())
        .as("Estimated offset should be close to the simulated 10s skew")
        .isBetween(
            simulatedSkew.minus(Duration.ofSeconds(2)).toMillis(),
            simulatedSkew.plus(Duration.ofSeconds(2)).toMillis());
  }

  @Test
  @DisplayName("Estimator handles HTTP errors gracefully and returns Optional.empty()")
  void httpError_returnsEmpty() {
    wm.stubFor(
        get("/clock").willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    assertThat(ClockSkewEstimator.estimateOffset(wm.baseUrl(), 3)).isEmpty();
  }

  @Test
  @DisplayName("Single sample works correctly")
  void singleSample_works() {
    stubDynamicClock(Duration.ZERO);

    Optional<Duration> offset = ClockSkewEstimator.estimateOffset(wm.baseUrl(), 1);

    assertThat(offset).isPresent();
    assertThat(offset.get().abs()).isLessThan(Duration.ofSeconds(1));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 5, 10})
  @DisplayName("Configurable number of samples all produce a reasonable result")
  void configurableSampleCount_producesResult(int samples) {
    stubDynamicClock(Duration.ZERO);

    Optional<Duration> offset = ClockSkewEstimator.estimateOffset(wm.baseUrl(), samples);

    assertThat(offset).isPresent();
    assertThat(offset.get().abs()).isLessThan(Duration.ofSeconds(2));
  }

  /**
   * WireMock response transformer that produces a fresh {@code ClockResponse} JSON body on every
   * request, with the server time set to {@code now() + skewMillis}.
   */
  static class DynamicClockTransformer implements ResponseDefinitionTransformerV2 {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
      ResponseDefinition original = serveEvent.getResponseDefinition();
      Parameters params = original.getTransformerParameters();
      long skewMillis =
          params == null ? 0L : ((Number) params.getOrDefault(PARAM_SKEW_MILLIS, 0L)).longValue();

      ZonedDateTime simulatedRemoteNow = ZonedDateTime.now().plus(Duration.ofMillis(skewMillis));
      String body;
      try {
        body =
            MAPPER.writeValueAsString(
                new TracingpointsController.ClockResponse(simulatedRemoteNow));
      } catch (JacksonException e) {
        throw new IllegalStateException("Failed to serialise ClockResponse", e);
      }
      return ResponseDefinitionBuilder.like(original).but().withBody(body).build();
    }

    @Override
    public String getName() {
      return CLOCK_TRANSFORMER;
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }
  }
}
