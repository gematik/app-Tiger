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
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpHeaderFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts OTLP/HTTP payloads into structured RBEL elements.
 */
@Slf4j
public class RbelOtlpConverter extends RbelConverterPlugin {

  private static final String CONTENT_TYPE_PROTOBUF = "application/x-protobuf";
  private static final String CONTENT_TYPE_PROTOBUF_ALT = "application/protobuf";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final JsonFormat.Printer JSON_PRINTER =
      JsonFormat.printer().preservingProtoFieldNames();
  private static final JsonFormat.Parser JSON_PARSER = JsonFormat.parser().ignoringUnknownFields();

  /**
   * Avoids attempting to parse oversized payloads by default.
   */
  @Override
  public boolean skipParsingOversizedContent() {
    return true;
  }

  /**
   * Parses OTLP request bodies that match supported content types and endpoints.
   *
   * @param rbelElement HTTP body element to inspect.
   * @param converter   conversion context used to build nested RBEL nodes.
   */
  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    var context = resolveOtlpContext(rbelElement);
    if (context.isEmpty() || rbelElement.getContent().isEmpty()) {
      return;
    }

    var otlpContext = context.get();
    try {
      var jsonPayload = decodePayload(rbelElement, otlpContext);
      if (jsonPayload == null || jsonPayload.isBlank()) {
        return;
      }
      var decodedElement =
          new RbelElement(jsonPayload.getBytes(StandardCharsets.UTF_8), rbelElement);
      converter.convertElement(decodedElement);
      rbelElement.addFacet(RbelOtlpFacet.builder()
          .decoded(decodedElement)
          .signal(otlpContext.signal())
          .contentType(otlpContext.contentType())
          .path(otlpContext.path())
          .build());
    } catch (Exception e) {
      log.debug(
          "Failed to decode OTLP {} payload for content type {}",
          otlpContext.signal(),
          otlpContext.contentType(),
          e);
    }
  }

  /**
   * Resolves OTLP parsing context from the parent HTTP request and headers.
   *
   * @param rbelElement HTTP body element.
   * @return optional OTLP context when request metadata is available.
   */
  private Optional<OtlpContext> resolveOtlpContext(RbelElement rbelElement) {
    return Optional.ofNullable(rbelElement.getParentNode())
        .flatMap(parent ->
            parent
                .getFacet(RbelHttpMessageFacet.class)
                .filter(facet -> facet.getBody() == rbelElement)
                .flatMap(facet ->
                    parent
                        .getFacet(RbelHttpRequestFacet.class)
                        .flatMap(requestFacet ->
                            buildContext(requestFacet, facet.getHeader()))));
  }

  /**
   * Builds OTLP parsing context based on the request path and content type.
   *
   * @param requestFacet  HTTP request facet providing path information.
   * @param headerElement HTTP header element containing content type.
   * @return optional OTLP context when the request matches OTLP criteria.
   */
  private Optional<OtlpContext> buildContext(
      RbelHttpRequestFacet requestFacet, RbelElement headerElement) {
    var path = requestFacet.getPathAsString();
    var signal = RbelOtlpSignal.fromPath(path);
    if (signal.isEmpty()) {
      return Optional.empty();
    }
    var headerFacet = headerElement.getFacet(RbelHttpHeaderFacet.class);
    if (headerFacet.isEmpty()) {
      return Optional.empty();
    }
    var contentType = headerFacet
        .get()
        .getCaseInsensitiveMatches("Content-Type")
        .map(RbelElement::getRawStringContent)
        .findFirst();
    if (contentType.isEmpty()) {
      return Optional.empty();
    }
    var contentTypeValue = contentType.get();
    var isProtobuf = isProtobufContentType(headerFacet.get());
    var isJson = isJsonContentType(headerFacet.get());
    if (isProtobuf || isJson) {
      return Optional.of(new OtlpContext(signal.get(), contentTypeValue, isProtobuf, path));
    }
    return Optional.empty();
  }

  /**
   * Checks whether the header indicates an OTLP protobuf payload.
   *
   * @param headerFacet HTTP header facet to inspect.
   * @return true when content type matches protobuf variants.
   */
  private boolean isProtobufContentType(RbelHttpHeaderFacet headerFacet) {
    return headerFacet.hasValueMatching("Content-Type", CONTENT_TYPE_PROTOBUF)
        || headerFacet.hasValueMatching("Content-Type", CONTENT_TYPE_PROTOBUF_ALT);
  }

  /**
   * Checks whether the header indicates an OTLP JSON payload.
   *
   * @param headerFacet HTTP header facet to inspect.
   * @return true when content type matches JSON.
   */
  private boolean isJsonContentType(RbelHttpHeaderFacet headerFacet) {
    return headerFacet.hasValueMatching("Content-Type", CONTENT_TYPE_JSON);
  }

  /**
   * Converts OTLP payload bytes into a JSON string representation.
   *
   * @param rbelElement HTTP body element.
   * @param context     resolved OTLP context (signal + encoding).
   * @return formatted JSON string payload.
   * @throws InvalidProtocolBufferException when protobuf decoding fails.
   */
  private String decodePayload(RbelElement rbelElement, OtlpContext context)
      throws InvalidProtocolBufferException {
    if (context.protobuf()) {
      return JSON_PRINTER.print(
          parseProtoPayload(context.signal(), rbelElement.getContent().toByteArray()));
    }
    var jsonPayload = rbelElement.getRawStringContent();
    if (jsonPayload == null) {
      return null;
    }
    var builder = newBuilderForSignal(context.signal());
    JSON_PARSER.merge(jsonPayload, builder);
    return JSON_PRINTER.print(builder.build());
  }

  /**
   * Parses protobuf bytes into the signal-specific OTLP request message.
   *
   * @param signal  OTLP signal type.
   * @param payload protobuf-encoded bytes.
   * @return parsed OTLP protobuf message.
   * @throws InvalidProtocolBufferException when parsing fails.
   */
  private Message parseProtoPayload(RbelOtlpSignal signal, byte[] payload)
      throws InvalidProtocolBufferException {
    return switch (signal) {
      case TRACES -> ExportTraceServiceRequest.parseFrom(payload);
      case METRICS -> ExportMetricsServiceRequest.parseFrom(payload);
      case LOGS -> ExportLogsServiceRequest.parseFrom(payload);
    };
  }

  /**
   * Returns a mutable protobuf builder for the given OTLP signal.
   *
   * @param signal OTLP signal type.
   * @return protobuf message builder for JSON parsing.
   */
  private Message.Builder newBuilderForSignal(RbelOtlpSignal signal) {
    return switch (signal) {
      case TRACES -> ExportTraceServiceRequest.newBuilder();
      case METRICS -> ExportMetricsServiceRequest.newBuilder();
      case LOGS -> ExportLogsServiceRequest.newBuilder();
    };
  }

  /**
   * Immutable context for OTLP decoding decisions.
   */
  private record OtlpContext(RbelOtlpSignal signal, String contentType, boolean protobuf,
                             String path) {

  }
}
