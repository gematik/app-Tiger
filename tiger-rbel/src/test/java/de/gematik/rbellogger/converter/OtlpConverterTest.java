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
package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;

import com.google.protobuf.util.JsonFormat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.facets.otlp.RbelOtlpFacet;
import de.gematik.rbellogger.facets.otlp.RbelOtlpSignal;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Coverage for OTLP/HTTP parsing of protobuf and JSON payloads.
 */
class OtlpConverterTest {

  /**
   * Ensures protobuf OTLP requests are detected and decoded.
   */
  @Test
  void convertOtlpProtobufRequest_shouldAddFacet() {
    var request = ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(ResourceSpans.newBuilder().build())
        .build();
    var converted = convertHttpRequest(
        "/v1/traces", "application/x-protobuf", request.toByteArray());

    var bodyElement = converted.getFirst("body").orElseThrow();
    assertThat(bodyElement).hasFacet(RbelOtlpFacet.class);
    Assertions.assertThat(bodyElement.getFacetOrFail(RbelOtlpFacet.class).getSignal())
        .isEqualTo(RbelOtlpSignal.TRACES);
    assertThat(bodyElement.getFacetOrFail(RbelOtlpFacet.class).getDecoded())
        .hasFacet(RbelJsonFacet.class);
  }

  /**
   * Ensures JSON OTLP requests are detected and decoded.
   */
  @Test
  void convertOtlpJsonRequest_shouldAddFacet() throws Exception {
    var request = ExportMetricsServiceRequest.newBuilder().build();
    var jsonPayload =
        JsonFormat.printer().preservingProtoFieldNames().print(request);

    var converted =
        convertHttpRequest("/v1/metrics", "application/json",
            jsonPayload.getBytes(StandardCharsets.UTF_8));

    var bodyElement = converted.getFirst("body").orElseThrow();
    assertThat(bodyElement).hasFacet(RbelOtlpFacet.class);
    Assertions.assertThat(bodyElement.getFacetOrFail(RbelOtlpFacet.class).getSignal())
        .isEqualTo(RbelOtlpSignal.METRICS);
    assertThat(bodyElement.getFacetOrFail(RbelOtlpFacet.class).getDecoded())
        .hasFacet(RbelJsonFacet.class);
  }

  /**
   * Builds and converts a minimal HTTP/1.1 request with the given body.
   */
  private RbelElement convertHttpRequest(String path, String contentType, byte[] body) {
    var output = new ByteArrayOutputStream();
    output.writeBytes(("POST " + path + " HTTP/1.1\r\n").getBytes(StandardCharsets.US_ASCII));
    output.writeBytes("Host: localhost\r\n".getBytes(StandardCharsets.US_ASCII));
    output.writeBytes(
        ("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.US_ASCII));
    output.writeBytes(
        ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    output.writeBytes(body);
    return RbelLogger.build().getRbelConverter().convertElement(output.toByteArray(), null);
  }
}
