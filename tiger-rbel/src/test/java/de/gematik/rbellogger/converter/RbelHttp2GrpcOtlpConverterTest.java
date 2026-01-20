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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.facets.grpc.RbelGrpcFrameFacet;
import de.gematik.rbellogger.facets.grpc.RbelGrpcMessageFacet;
import de.gematik.rbellogger.facets.http2.RbelHttp2MessageFacet;
import de.gematik.rbellogger.facets.otlp.RbelOtlpFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RbelHttp2GrpcOtlpConverterTest {

  private static final byte[] PREFACE =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
  private static final int FRAME_HEADERS = 0x1;
  private static final int FRAME_DATA = 0x0;
  private static final int FLAG_END_STREAM = 0x1;
  private static final int FLAG_END_HEADERS = 0x4;

  /**
   * Validate that HTTP/2 gRPC OTLP requests are parsed into RBEL facets.
   */
  @Test
  void http2GrpcOtlpRequestShouldBeParsed() {
    var logger = RbelLogger.build();
    var request = buildTraceRequest();
    var grpcPayload = wrapGrpcMessage(request.toByteArray(), false);
    var headers = buildGrpcHeaders(grpcPayload.length);
    byte[] headersFrame = buildHeadersFrame(1, headers, false);
    byte[] dataFrame = buildDataFrame(1, grpcPayload, true);
    byte[] http2Message = concat(PREFACE, headersFrame, dataFrame);
    var metadata =
        new RbelMessageMetadata()
            .withSender(RbelSocketAddress.fromString("127.0.0.1:1111").orElse(null))
            .withReceiver(RbelSocketAddress.fromString("127.0.0.1:4317").orElse(null));

    RbelElement parsed = logger.getRbelConverter().parseMessage(http2Message, metadata);

    assertThat(parsed.hasFacet(RbelHttp2MessageFacet.class)).isTrue();
    assertThat(parsed.hasFacet(RbelGrpcMessageFacet.class)).isTrue();

    var payload = firstGrpcPayload(parsed);
    assertThat(payload.hasFacet(RbelOtlpFacet.class)).isTrue();
    assertThat(payload.getFacetOrFail(RbelOtlpFacet.class).signal().getRawStringContent())
        .isEqualTo("traces");
  }

  /**
   * Validate that OTLP metrics are decoded from gRPC payloads.
   */
  @Test
  void http2GrpcOtlpMetricsShouldBeParsed() {
    var logger = RbelLogger.build();
    var request = buildMetricsRequest();
    var grpcPayload = wrapGrpcMessage(request.toByteArray(), false);
    var headers = buildGrpcMetricsHeaders(grpcPayload.length);
    byte[] headersFrame = buildHeadersFrame(1, headers, false);
    byte[] dataFrame = buildDataFrame(1, grpcPayload, true);
    byte[] http2Message = concat(PREFACE, headersFrame, dataFrame);
    var metadata =
        new RbelMessageMetadata()
            .withSender(RbelSocketAddress.fromString("127.0.0.1:1111").orElse(null))
            .withReceiver(RbelSocketAddress.fromString("127.0.0.1:4317").orElse(null));

    RbelElement parsed = logger.getRbelConverter().parseMessage(http2Message, metadata);

    var payload = firstGrpcPayload(parsed);
    assertThat(payload.hasFacet(RbelOtlpFacet.class)).isTrue();
    assertThat(payload.getFacetOrFail(RbelOtlpFacet.class).signal().getRawStringContent())
        .isEqualTo("metrics");
  }

  /**
   * Validate that trailers are parsed into gRPC status fields.
   */
  @Test
  void http2GrpcTrailersShouldBeParsed() {
    var logger = RbelLogger.build();
    var request = buildTraceRequest();
    var grpcPayload = wrapGrpcMessage(request.toByteArray(), false);
    var headers = buildGrpcHeaders(grpcPayload.length);
    var trailers = buildGrpcTrailerHeaders();
    byte[] headersFrame = buildHeadersFrame(1, headers, false);
    byte[] dataFrame = buildDataFrame(1, grpcPayload, false);
    byte[] trailerFrame = buildHeadersFrame(1, trailers, true);
    byte[] http2Message = concat(PREFACE, headersFrame, dataFrame, trailerFrame);
    var metadata =
        new RbelMessageMetadata()
            .withSender(RbelSocketAddress.fromString("127.0.0.1:1111").orElse(null))
            .withReceiver(RbelSocketAddress.fromString("127.0.0.1:4317").orElse(null));

    RbelElement parsed = logger.getRbelConverter().parseMessage(http2Message, metadata);

    var grpcFacet = parsed.getFacetOrFail(RbelGrpcMessageFacet.class);
    assertThat(grpcFacet.status().getRawStringContent()).isEqualTo("0");
  }

  /**
   * Validate that compressed gRPC payloads attach a warning note.
   */
  @Test
  void grpcCompressedPayloadShouldAddWarningNote() {
    var logger = RbelLogger.build();
    var request = buildLogsRequest();
    var grpcPayload = wrapGrpcMessage(request.toByteArray(), true);
    var headers = buildGrpcLogsHeaders(grpcPayload.length);
    byte[] headersFrame = buildHeadersFrame(1, headers, false);
    byte[] dataFrame = buildDataFrame(1, grpcPayload, true);
    byte[] http2Message = concat(PREFACE, headersFrame, dataFrame);
    var metadata =
        new RbelMessageMetadata()
            .withSender(RbelSocketAddress.fromString("127.0.0.1:1111").orElse(null))
            .withReceiver(RbelSocketAddress.fromString("127.0.0.1:4317").orElse(null));

    RbelElement parsed = logger.getRbelConverter().parseMessage(http2Message, metadata);

    var payload = firstGrpcPayload(parsed);
    var note = payload.getFacet(RbelNoteFacet.class);
    assertThat(note).isPresent();
    assertThat(note.get().getStyle()).isEqualTo(RbelNoteFacet.NoteStyling.WARN);
  }

  /**
   * Wrap protobuf payload bytes into a gRPC envelope.
   *
   * @param payload the raw protobuf payload
   * @param compressed true to mark the payload as compressed
   * @return the framed gRPC payload
   */
  private byte[] wrapGrpcMessage(byte[] payload, boolean compressed) {
    var buffer = ByteBuffer.allocate(1 + 4 + payload.length);
    buffer.put(compressed ? (byte) 1 : (byte) 0);
    buffer.putInt(payload.length);
    buffer.put(payload);
    return buffer.array();
  }

  /**
   * Build gRPC headers for an OTLP trace export request.
   *
   * @param contentLength the payload size
   * @return HTTP/2 headers
   */
  private Http2Headers buildGrpcHeaders(int contentLength) {
    return new DefaultHttp2Headers()
        .method("POST")
        .path("/opentelemetry.proto.collector.trace.v1.TraceService/Export")
        .scheme("http")
        .authority("localhost:4317")
        .add("content-type", "application/grpc")
        .add("te", "trailers")
        .add("content-length", Integer.toString(contentLength));
  }

  /**
   * Build gRPC headers for an OTLP metrics export request.
   *
   * @param contentLength the payload size
   * @return HTTP/2 headers
   */
  private Http2Headers buildGrpcMetricsHeaders(int contentLength) {
    return new DefaultHttp2Headers()
        .method("POST")
        .path("/opentelemetry.proto.collector.metrics.v1.MetricsService/Export")
        .scheme("http")
        .authority("localhost:4317")
        .add("content-type", "application/grpc")
        .add("te", "trailers")
        .add("content-length", Integer.toString(contentLength));
  }

  /**
   * Build gRPC headers for an OTLP logs export request.
   *
   * @param contentLength the payload size
   * @return HTTP/2 headers
   */
  private Http2Headers buildGrpcLogsHeaders(int contentLength) {
    return new DefaultHttp2Headers()
        .method("POST")
        .path("/opentelemetry.proto.collector.logs.v1.LogsService/Export")
        .scheme("http")
        .authority("localhost:4317")
        .add("content-type", "application/grpc")
        .add("te", "trailers")
        .add("content-length", Integer.toString(contentLength));
  }

  /**
   * Build gRPC trailers indicating a successful response.
   *
   * @return HTTP/2 trailers
   */
  private Http2Headers buildGrpcTrailerHeaders() {
    return new DefaultHttp2Headers()
        .add("grpc-status", "0")
        .add("grpc-message", "OK");
  }

  /**
   * Build a HEADERS frame with HPACK encoding.
   *
   * @param streamId  the stream identifier
   * @param headers   the HTTP/2 headers
   * @param endStream whether to set END_STREAM
   * @return the serialized HEADERS frame
   */
  private byte[] buildHeadersFrame(int streamId, Http2Headers headers, boolean endStream) {
    ByteBuf encoded = Unpooled.buffer();
    byte[] headerBlock;
    try (var encoder = new DefaultHttp2HeadersEncoder()) {
      try {
        encoder.encodeHeaders(streamId, headers, encoded);
        headerBlock = ByteBufUtil.getBytes(encoded);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to encode HTTP/2 headers", e);
      } finally {
        encoded.release();
      }
    }
    int flags = FLAG_END_HEADERS | (endStream ? FLAG_END_STREAM : 0);
    return buildFrame(FRAME_HEADERS, flags, streamId, headerBlock);
  }

  /**
   * Build a DATA frame.
   *
   * @param streamId  the stream identifier
   * @param data      the frame payload
   * @param endStream whether to set END_STREAM
   * @return the serialized DATA frame
   */
  private byte[] buildDataFrame(int streamId, byte[] data, boolean endStream) {
    int flags = endStream ? FLAG_END_STREAM : 0;
    return buildFrame(FRAME_DATA, flags, streamId, data);
  }

  /**
   * Build an HTTP/2 frame from header fields and payload.
   *
   * @param type     the frame type
   * @param flags    the frame flags
   * @param streamId the stream identifier
   * @param payload  the frame payload
   * @return the serialized frame
   */
  private byte[] buildFrame(int type, int flags, int streamId, byte[] payload) {
    int length = payload.length;
    byte[] frame = new byte[9 + length];
    frame[0] = (byte) ((length >> 16) & 0xFF);
    frame[1] = (byte) ((length >> 8) & 0xFF);
    frame[2] = (byte) (length & 0xFF);
    frame[3] = (byte) type;
    frame[4] = (byte) flags;
    frame[5] = (byte) ((streamId >> 24) & 0x7F);
    frame[6] = (byte) ((streamId >> 16) & 0xFF);
    frame[7] = (byte) ((streamId >> 8) & 0xFF);
    frame[8] = (byte) (streamId & 0xFF);
    System.arraycopy(payload, 0, frame, 9, payload.length);
    return frame;
  }

  /**
   * Concatenate HTTP/2 byte arrays into one message.
   *
   * @param parts the message parts
   * @return the combined byte array
   */
  private byte[] concat(byte[]... parts) {
    int totalLength = 0;
    for (var part : parts) {
      totalLength += part.length;
    }
    byte[] result = new byte[totalLength];
    int offset = 0;
    for (var part : parts) {
      System.arraycopy(part, 0, result, offset, part.length);
      offset += part.length;
    }
    return result;
  }

  /**
   * Build a minimal trace request with at least one ResourceSpans entry.
   *
   * @return the trace request
   */
  private ExportTraceServiceRequest buildTraceRequest() {
    return ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(ResourceSpans.newBuilder().build())
        .build();
  }

  /**
   * Build a minimal metrics request with at least one ResourceMetrics entry.
   *
   * @return the metrics request
   */
  private ExportMetricsServiceRequest buildMetricsRequest() {
    return ExportMetricsServiceRequest.newBuilder()
        .addResourceMetrics(ResourceMetrics.newBuilder().build())
        .build();
  }

  /**
   * Build a minimal logs request with at least one ResourceLogs entry.
   *
   * @return the logs request
   */
  private ExportLogsServiceRequest buildLogsRequest() {
    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(ResourceLogs.newBuilder().build())
        .build();
  }

  /**
   * Extract the first gRPC payload element from a parsed message.
   *
   * @param parsed the parsed message element
   * @return the payload element
   */
  private RbelElement firstGrpcPayload(RbelElement parsed) {
    var grpcFacet = parsed.getFacetOrFail(RbelGrpcMessageFacet.class);
    var frames = grpcFacet
        .frames()
        .getFacet(RbelListFacet.class)
        .map(RbelListFacet::getChildNodes)
        .orElse(List.of());
    assertThat(frames).isNotEmpty();
    return frames.get(0).getFacetOrFail(RbelGrpcFrameFacet.class).payload();
  }
}
