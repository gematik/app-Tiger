/*
 *  Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Diagnostic test to measure if 100-200ms overhead is in Tiger proxy or backend simulator.
 *
 * <p>This test: 1. Measures direct to backend (baseline) 2. Measures through Tiger proxy 3. Reports
 * the difference to identify bottleneck
 */
@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestHttpProxyOverhead extends AbstractTigerProxyTest {

  private static final int REQUEST_COUNT = 10;
  private static final String BACKEND_HOST = "127.0.0.1";
  private static final String ENDPOINT = "/foobar";

  /**
   * Compares direct backend access vs. through proxy to identify where 100-200ms overhead comes
   * from.
   *
   * <p>Expected results: - Direct to backend: ~10-50ms (baseline) - Through proxy: 100-200ms
   * (current observation) - Difference tells us if it's Tiger or backend slowness
   */
  @Test
  void diagnoseHttpProxyOverhead() throws Exception {
    log.info("=== SCENARIO 1: Direct to backend ===");
    log.info(
        "Making {} direct requests to http://{}:{}{}",
        REQUEST_COUNT,
        BACKEND_HOST,
        fakeBackendServerPort,
        ENDPOINT);

    List<Long> directTimings = new ArrayList<>();
    int directSuccess = 0;
    for (int i = 0; i < REQUEST_COUNT; i++) {
      long reqStart = System.currentTimeMillis();
      try {
        var response =
            unirestInstance
                .post("http://" + BACKEND_HOST + ":" + fakeBackendServerPort + ENDPOINT)
                .body("{}")
                .asString();
        long reqEnd = System.currentTimeMillis();
        long timing = reqEnd - reqStart;
        directTimings.add(timing);
        directSuccess++;
        log.info("  Request {}: {}ms (status: {})", i + 1, timing, response.getStatus());
      } catch (Exception e) {
        long reqEnd = System.currentTimeMillis();
        log.warn("  Request {} FAILED after {}ms: {}", i + 1, reqEnd - reqStart, e.getMessage());
      }
    }

    double directAvg = directTimings.stream().mapToLong(Long::longValue).average().orElse(-1);
    log.info(
        "Direct baseline: {} successful requests, avg: {}ms",
        directSuccess,
        String.format("%.1f", directAvg));

    log.info("=== SCENARIO 2: Through Tiger proxy ===");

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://" + BACKEND_HOST + ":" + fakeBackendServerPort)
                        .build()))
            .build());

    log.info(
        "Making {} requests through proxy to http://localhost:{}{}",
        REQUEST_COUNT,
        tigerProxy.getProxyPort(),
        ENDPOINT);

    List<Long> proxyTimings = new ArrayList<>();
    int proxySuccess = 0;
    for (int i = 0; i < REQUEST_COUNT; i++) {
      long reqStart = System.currentTimeMillis();
      try {
        var response =
            unirestInstance
                .post("http://localhost:" + tigerProxy.getProxyPort() + ENDPOINT)
                .body("{}")
                .asString();
        long reqEnd = System.currentTimeMillis();
        long timing = reqEnd - reqStart;
        proxyTimings.add(timing);
        proxySuccess++;
        log.info("  Request {}: {}ms (status: {})", i + 1, timing, response.getStatus());
      } catch (Exception e) {
        long reqEnd = System.currentTimeMillis();
        log.warn("  Request {} FAILED after {}ms: {}", i + 1, reqEnd - reqStart, e.getMessage());
      }
    }

    double proxyAvg = proxyTimings.stream().mapToLong(Long::longValue).average().orElse(-1);
    log.info(
        "Through proxy: {} successful requests, avg: {}ms",
        proxySuccess,
        String.format("%.1f", proxyAvg));

    double overhead = proxyAvg - directAvg;
    log.info("=== ANALYSIS ===");
    log.info("Direct baseline:    {}ms", String.format("%.1f", directAvg));
    log.info("Through proxy:      {}ms", String.format("%.1f", proxyAvg));
    log.info("Proxy overhead:     {}ms", String.format("%.1f", overhead));
    log.info("");

    assertThat(overhead)
        .as(
            "Proxy overhead should be <100ms on average."
                + " Direct: %.1fms, Through proxy: %.1fms, Overhead: %.1fms",
            directAvg, proxyAvg, overhead)
        .isLessThan(100);

    if (overhead < 20) {
      log.info("✓ Overhead is acceptable (<20ms). Slowness is likely backend processing.");
    } else if (overhead < 50) {
      log.info("⚠ Moderate overhead (20-50ms). Likely TLS handshake or keep-alive not working.");
    } else {
      log.info("✗ High overhead (>50ms). Tiger proxy or connection pooling issue.");
    }

    if (directAvg > 100) {
      log.info("→ Backend itself is slow (>100ms direct). Not a Tiger issue.");
    }
  }
}
