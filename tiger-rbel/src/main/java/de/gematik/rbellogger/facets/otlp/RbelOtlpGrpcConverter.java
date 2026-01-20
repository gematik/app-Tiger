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
package de.gematik.rbellogger.facets.otlp;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.facets.grpc.RbelGrpcConverter;
import de.gematik.rbellogger.facets.grpc.RbelGrpcFrameFacet;
import de.gematik.rbellogger.facets.grpc.RbelGrpcMessageFacet;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Converter decoding OTLP protobuf payloads carried in gRPC frames.
 */
@ConverterInfo(dependsOn = {RbelGrpcConverter.class})
public class RbelOtlpGrpcConverter extends RbelConverterPlugin {

  /**
   * {@inheritDoc}
   */
  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_PARSING;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    var grpcFacet = rbelElement.getFacet(RbelGrpcMessageFacet.class).orElse(null);
    if (grpcFacet == null) {
      return;
    }
    var path = Optional.ofNullable(grpcFacet.path()).map(RbelElement::getRawStringContent)
        .orElse("");
    var signal = OtlpSignal.fromPath(path);
    if (signal.isEmpty()) {
      return;
    }
    var framesFacet = grpcFacet.frames().getFacet(RbelListFacet.class);
    if (framesFacet.isEmpty()) {
      return;
    }
    framesFacet.get().getChildNodes().forEach(frameElement ->
        frameElement
            .getFacet(RbelGrpcFrameFacet.class)
            .map(RbelGrpcFrameFacet::payload)
            .ifPresent(payload -> decodeOtlpPayload(payload, signal.get(), converter, path)));
  }

  /**
   * Decode the OTLP payload and attach an OTLP facet to the payload element.
   *
   * @param payloadElement the gRPC payload element
   * @param signal         the OTLP signal type
   * @param converter      the conversion executor
   * @param servicePath    the gRPC service path
   */
  private void decodeOtlpPayload(
      RbelElement payloadElement,
      OtlpSignal signal,
      RbelConversionExecutor converter,
      String servicePath) {
    var payload = payloadElement.getRawContent();
    if (payload == null || payload.length == 0) {
      return;
    }
    try {
      Message message = signal.parse(payload);
      String json =
          JsonFormat.printer().preservingProtoFieldNames().omittingInsignificantWhitespace()
              .print(message);
      var jsonElement = new RbelElement(json.getBytes(StandardCharsets.UTF_8), payloadElement);
      payloadElement.addFacet(RbelOtlpFacet.builder()
          .signal(RbelElement.wrap(payloadElement, signal.label()))
          .service(RbelElement.wrap(payloadElement, servicePath))
          .payload(jsonElement)
          .build());
      converter.convertElement(jsonElement);
    } catch (InvalidProtocolBufferException e) {
      payloadElement.addFacet(RbelNoteFacet.builder()
          .style(NoteStyling.WARN)
          .value("Failed to decode OTLP payload: " + e.getMessage())
          .build());
    }
  }

  /**
   * Supported OTLP signal types.
   */
  private enum OtlpSignal {
    TRACES(
        "traces",
        "/opentelemetry.proto.collector.trace.v1.TraceService/Export") {
      @Override
      Message parse(byte[] payload) throws InvalidProtocolBufferException {
        return ExportTraceServiceRequest.parseFrom(payload);
      }
    },
    METRICS(
        "metrics",
        "/opentelemetry.proto.collector.metrics.v1.MetricsService/Export") {
      @Override
      Message parse(byte[] payload) throws InvalidProtocolBufferException {
        return ExportMetricsServiceRequest.parseFrom(payload);
      }
    },
    LOGS(
        "logs",
        "/opentelemetry.proto.collector.logs.v1.LogsService/Export") {
      @Override
      Message parse(byte[] payload) throws InvalidProtocolBufferException {
        return ExportLogsServiceRequest.parseFrom(payload);
      }
    };

    private final String label;
    private final String path;

    /**
     * Create an OTLP signal mapping.
     *
     * @param label the signal label
     * @param path  the gRPC path
     */
    OtlpSignal(String label, String path) {
      this.label = label;
      this.path = path;
    }

    /**
     * Find the OTLP signal for a gRPC path.
     *
     * @param path the gRPC path
     * @return the matching signal if found
     */
    static Optional<OtlpSignal> fromPath(String path) {
      return Stream.of(values()).filter(signal -> signal.path.equals(path)).findFirst();
    }

    /**
     * Parse the OTLP payload.
     *
     * @param payload the raw protobuf payload
     * @return the decoded message
     */
    abstract Message parse(byte[] payload) throws InvalidProtocolBufferException;

    /**
     * Provide a display label for the signal.
     *
     * @return the signal label
     */
    String label() {
      return label;
    }
  }
}
