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
package de.gematik.rbellogger.facets.http2;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpHeaderFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersDecoder;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts HTTP/2 frames into a structured RBEL representation with headers and body.
 */
@Slf4j
@ConverterInfo
public class RbelHttp2Converter extends RbelConverterPlugin {

  private static final byte[] HTTP2_PREFACE =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
  private static final int FRAME_HEADER_LENGTH = 9;
  private static final int FLAG_END_STREAM = 0x1;
  private static final int FLAG_END_HEADERS = 0x4;
  private static final int FLAG_PADDED = 0x8;
  private static final int FLAG_PRIORITY = 0x20;
  private static final int FRAME_DATA = 0x0;
  private static final int FRAME_HEADERS = 0x1;
  private static final int FRAME_CONTINUATION = 0x9;
  private static final int FRAME_SETTINGS = 0x4;
  private static final int FRAME_PING = 0x6;
  private static final int FRAME_GOAWAY = 0x7;
  private static final int FRAME_WINDOW_UPDATE = 0x8;
  private static final int FRAME_RST_STREAM = 0x3;
  private static final int FRAME_PRIORITY = 0x2;
  private static final int FRAME_PUSH_PROMISE = 0x5;
  private static final int MAX_HEADER_LIST_SIZE = 16384;

  private final Map<TcpIpConnectionIdentifier, Http2ConnectionState> connectionStates =
      new ConcurrentHashMap<>();
  private final AtomicBoolean callbacksRegistered = new AtomicBoolean(false);

  /**
   * Decode a frame header at the given offset.
   *
   * @param content the raw content buffer
   * @param offset  the start offset
   * @return the parsed header when complete
   */
  private static Optional<Http2FrameHeader> parseFrameHeader(RbelContent content, int offset) {
    if (content.size() < offset + FRAME_HEADER_LENGTH) {
      return Optional.empty();
    }
    int length =
        (Byte.toUnsignedInt(content.get(offset)) << 16)
            | (Byte.toUnsignedInt(content.get(offset + 1)) << 8)
            | Byte.toUnsignedInt(content.get(offset + 2));
    byte type = content.get(offset + 3);
    byte flags = content.get(offset + 4);
    int streamId =
        (Byte.toUnsignedInt(content.get(offset + 5)) & 0x7F) << 24
            | (Byte.toUnsignedInt(content.get(offset + 6)) << 16)
            | (Byte.toUnsignedInt(content.get(offset + 7)) << 8)
            | Byte.toUnsignedInt(content.get(offset + 8));
    return Optional.of(new Http2FrameHeader(length, type, flags, streamId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (!shouldAttemptParsing(rbelElement)) {
      return;
    }
    registerCallbacksIfNeeded(converter);
    var connectionIdentifier = rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getTcpIpConnectionIdentifier)
        .orElse(null);
    if (connectionIdentifier == null) {
      return;
    }
    var state =
        connectionStates.computeIfAbsent(connectionIdentifier,
            unused -> new Http2ConnectionState());
    var parsedMessage = state.parse(rbelElement.getContent());
    if (parsedMessage.isEmpty()) {
      return;
    }
    applyParsedMessage(rbelElement, converter, parsedMessage.get());
  }

  /**
   * Determine whether the element should be parsed for HTTP/2 frames.
   *
   * @param rbelElement the element to inspect
   * @return true when HTTP/2 parsing should be attempted
   */
  private boolean shouldAttemptParsing(RbelElement rbelElement) {
    if (rbelElement.getParentNode() != null) {
      return false;
    }
    if (rbelElement.hasFacet(RbelHttpMessageFacet.class)
        || rbelElement.hasFacet(RbelHttp2MessageFacet.class)) {
      return false;
    }
    if (rbelElement.getContent().size() < FRAME_HEADER_LENGTH) {
      return false;
    }
    return looksLikeHttp2(rbelElement.getContent());
  }

  /**
   * Register clear-history callbacks once to keep HTTP/2 state bounded.
   *
   * @param converter the conversion executor to access callbacks
   */
  private void registerCallbacksIfNeeded(RbelConversionExecutor converter) {
    if (callbacksRegistered.compareAndSet(false, true)) {
      converter.getConverter().addClearHistoryCallback(connectionStates::clear);
    }
  }

  /**
   * Decide if the buffer looks like HTTP/2 traffic without parsing it fully.
   *
   * @param content the raw TCP content
   * @return true if the content matches HTTP/2 preface or a valid frame header
   */
  private boolean looksLikeHttp2(RbelContent content) {
    if (content.startsWith(HTTP2_PREFACE)) {
      return true;
    }
    if (content.size() < FRAME_HEADER_LENGTH) {
      return false;
    }
    var header = parseFrameHeader(content, 0);
    return header.isPresent() && isKnownFrameType(header.get().type());
  }

  /**
   * Apply the parsed HTTP/2 message to the RBEL element.
   *
   * @param targetElement the element to enrich
   * @param converter     the conversion executor
   * @param parsedMessage the parsed HTTP/2 message
   */
  private void applyParsedMessage(
      RbelElement targetElement, RbelConversionExecutor converter,
      ParsedHttp2Message parsedMessage) {
    var streamState = parsedMessage.streamState();
    targetElement.setUsedBytes(parsedMessage.bytesConsumed());
    var headerElement = buildHeaderElement(targetElement, streamState.headers());
    var trailerElement = buildHeaderElement(targetElement, streamState.trailers());
    var bodyElement = buildBodyElement(targetElement, streamState.data());
    var streamIdElement =
        RbelElement.wrap(targetElement, Integer.toString(parsedMessage.streamId()));
    var httpVersionElement = new RbelElement("HTTP/2".getBytes(StandardCharsets.US_ASCII),
        targetElement);
    var http2Facet = RbelHttp2MessageFacet.builder()
        .streamId(streamIdElement)
        .headers(headerElement)
        .body(bodyElement)
        .trailers(trailerElement)
        .httpVersion(httpVersionElement)
        .build();
    targetElement.addFacet(http2Facet);
    targetElement.addFacet(new RbelRootFacet<>(http2Facet));
    addRequestOrResponseFacet(targetElement, streamState.headers());
    converter.convertElement(bodyElement);
  }

  /**
   * Build a header element with HTTP header facet from HTTP/2 headers.
   *
   * @param parent  the parent element
   * @param headers the HTTP/2 headers to wrap
   * @return an RBEL element representing headers
   */
  private RbelElement buildHeaderElement(RbelElement parent, Http2Headers headers) {
    var headerElement = new RbelElement(new byte[]{}, parent);
    if (headers == null) {
      return headerElement;
    }
    var map = new RbelMultiMap<RbelElement>();
    for (var entry : headers) {
      map.put(entry.getKey().toString(),
          RbelElement.wrap(headerElement, entry.getValue().toString()));
    }
    headerElement.addFacet(new RbelHttpHeaderFacet(map));
    return headerElement;
  }

  /**
   * Build the body element from decoded DATA frame bytes.
   *
   * @param parent the parent element
   * @param data   the stream payload
   * @return an RBEL element for the body payload
   */
  private RbelElement buildBodyElement(RbelElement parent, byte[] data) {
    return new RbelElement(data == null ? new byte[]{} : data, parent);
  }

  /**
   * Add request or response facet based on HTTP/2 pseudo headers.
   *
   * @param targetElement the element to enrich
   * @param headers       the HTTP/2 headers
   */
  private void addRequestOrResponseFacet(RbelElement targetElement, Http2Headers headers) {
    if (headers == null) {
      return;
    }
    var method = headerValue(headers, ":method");
    var path = headerValue(headers, ":path");
    var status = headerValue(headers, ":status");
    if (method.isPresent()) {
      targetElement.addFacet(
          new RbelRequestFacet(method.get() + " " + path.orElse(""), true));
    } else {
      status.ifPresent(s -> targetElement.addFacet(new RbelResponseFacet(s)));
    }
  }

  /**
   * Extract a header value from an HTTP/2 header map.
   *
   * @param headers the header collection
   * @param name    the header name to look up
   * @return the header value if present
   */
  private Optional<String> headerValue(Http2Headers headers, String name) {
    return Optional.ofNullable(headers.get(name)).map(CharSequence::toString);
  }

  /**
   * Check if a frame type is recognized as HTTP/2.
   *
   * @param type the frame type
   * @return true when the type is known
   */
  private boolean isKnownFrameType(byte type) {
    return switch (Byte.toUnsignedInt(type)) {
      case FRAME_DATA,
           FRAME_HEADERS,
           FRAME_CONTINUATION,
           FRAME_SETTINGS,
           FRAME_PING,
           FRAME_GOAWAY,
           FRAME_WINDOW_UPDATE,
           FRAME_RST_STREAM,
           FRAME_PRIORITY,
           FRAME_PUSH_PROMISE -> true;
      default -> false;
    };
  }

  /**
   * Record capturing a parsed HTTP/2 frame header.
   */
  private record Http2FrameHeader(int length, byte type, byte flags, int streamId) {

  }

  /**
   * Record capturing a completed HTTP/2 stream message.
   */
  private record ParsedHttp2Message(int bytesConsumed, int streamId, Http2StreamState streamState) {

  }

  /**
   * State shared across frames for a single connection direction.
   */
  private static class Http2ConnectionState {

    private final Http2HeadersDecoder headersDecoder =
        new DefaultHttp2HeadersDecoder(false, MAX_HEADER_LIST_SIZE);
    private final Map<Integer, Http2StreamState> streams = new HashMap<>();
    private boolean prefaceConsumed = false;

    /**
     * Parse frames from the content buffer until a full stream is reconstructed.
     *
     * @param content the buffered TCP bytes
     * @return the next completed stream if available
     */
    private Optional<ParsedHttp2Message> parse(RbelContent content) {
      int offset = 0;
      if (!prefaceConsumed && content.startsWith(HTTP2_PREFACE)) {
        offset += HTTP2_PREFACE.length;
        prefaceConsumed = true;
      }
      while (offset + FRAME_HEADER_LENGTH <= content.size()) {
        var headerOpt = parseFrameHeader(content, offset);
        if (headerOpt.isEmpty()) {
          return Optional.empty();
        }
        var header = headerOpt.get();
        int frameLength = FRAME_HEADER_LENGTH + header.length();
        if (offset + frameLength > content.size()) {
          return Optional.empty();
        }
        byte[] payload =
            content.toByteArray(offset + FRAME_HEADER_LENGTH, offset + frameLength);
        offset += frameLength;
        var completedStream = handleFrame(header, payload);
        if (completedStream.isPresent()) {
          streams.remove(header.streamId());
          return Optional.of(
              new ParsedHttp2Message(offset, header.streamId(), completedStream.get()));
        }
      }
      return Optional.empty();
    }

    /**
     * Handle a single HTTP/2 frame and update stream state.
     *
     * @param header  the parsed frame header
     * @param payload the frame payload
     * @return the completed stream state when end stream is detected
     */
    private Optional<Http2StreamState> handleFrame(Http2FrameHeader header, byte[] payload) {
      return switch (Byte.toUnsignedInt(header.type())) {
        case FRAME_DATA -> handleDataFrame(header, payload);
        case FRAME_HEADERS -> handleHeadersFrame(header, payload);
        case FRAME_CONTINUATION -> handleContinuationFrame(header, payload);
        default -> Optional.empty();
      };
    }

    /**
     * Handle a DATA frame for a stream.
     *
     * @param header  the parsed frame header
     * @param payload the frame payload
     * @return completed stream state if end stream is reached
     */
    private Optional<Http2StreamState> handleDataFrame(Http2FrameHeader header, byte[] payload) {
      if (header.streamId() == 0) {
        return Optional.empty();
      }
      var streamState = streams.computeIfAbsent(header.streamId(),
          unused -> new Http2StreamState());
      var data = stripPadding(payload, header.flags());
      streamState.appendData(data);
      if ((header.flags() & FLAG_END_STREAM) != 0) {
        streamState.markEndStream();
      }
      return streamState.isComplete() ? Optional.of(streamState) : Optional.empty();
    }

    /**
     * Handle a HEADERS frame for a stream.
     *
     * @param header  the parsed frame header
     * @param payload the frame payload
     * @return completed stream state if end stream is reached
     */
    private Optional<Http2StreamState> handleHeadersFrame(Http2FrameHeader header, byte[] payload) {
      if (header.streamId() == 0) {
        return Optional.empty();
      }
      var streamState = streams.computeIfAbsent(header.streamId(),
          unused -> new Http2StreamState());
      var fragment = extractHeadersFragment(payload, header.flags());
      streamState.appendHeaderBlock(fragment);
      if ((header.flags() & FLAG_END_HEADERS) != 0) {
        decodeAndStoreHeaders(streamState, header.streamId());
      }
      if ((header.flags() & FLAG_END_STREAM) != 0) {
        streamState.markEndStream();
      }
      return streamState.isComplete() ? Optional.of(streamState) : Optional.empty();
    }

    /**
     * Handle a CONTINUATION frame for a stream.
     *
     * @param header  the parsed frame header
     * @param payload the frame payload
     * @return completed stream state if end stream is reached
     */
    private Optional<Http2StreamState> handleContinuationFrame(
        Http2FrameHeader header, byte[] payload) {
      if (header.streamId() == 0) {
        return Optional.empty();
      }
      var streamState = streams.computeIfAbsent(header.streamId(),
          unused -> new Http2StreamState());
      if (!streamState.isHeaderBlockInProgress()) {
        return Optional.empty();
      }
      streamState.appendHeaderBlock(payload);
      if ((header.flags() & FLAG_END_HEADERS) != 0) {
        decodeAndStoreHeaders(streamState, header.streamId());
      }
      return streamState.isComplete() ? Optional.of(streamState) : Optional.empty();
    }

    /**
     * Decode and store the accumulated header block for a stream.
     *
     * @param streamState the stream state
     * @param streamId    the stream identifier
     */
    private void decodeAndStoreHeaders(Http2StreamState streamState, int streamId) {
      byte[] headerBlock = streamState.consumeHeaderBlock();
      if (headerBlock.length == 0) {
        return;
      }
      ByteBuf headerBuf = Unpooled.wrappedBuffer(headerBlock);
      try {
        var decoded = headersDecoder.decodeHeaders(streamId, headerBuf);
        streamState.storeHeaders(decoded);
      } catch (Http2Exception e) {
        log.warn("Failed to decode HTTP/2 headers for stream {}", streamId, e);
      } finally {
        headerBuf.release();
      }
    }

    /**
     * Extract the header block fragment from a HEADERS frame payload.
     *
     * @param payload the HEADERS frame payload
     * @param flags   the frame flags
     * @return the header block fragment
     */
    private byte[] extractHeadersFragment(byte[] payload, byte flags) {
      int offset = 0;
      int padLength = 0;
      if ((flags & FLAG_PADDED) != 0) {
        padLength = Byte.toUnsignedInt(payload[offset]);
        offset += 1;
      }
      if ((flags & FLAG_PRIORITY) != 0) {
        offset += 5;
      }
      int fragmentLength = payload.length - offset - padLength;
      if (fragmentLength <= 0) {
        return new byte[]{};
      }
      return Arrays.copyOfRange(payload, offset, offset + fragmentLength);
    }

    /**
     * Strip padding from a DATA frame payload.
     *
     * @param payload the DATA frame payload
     * @param flags   the DATA frame flags
     * @return the unpadded data bytes
     */
    private byte[] stripPadding(byte[] payload, byte flags) {
      int offset = 0;
      int padLength = 0;
      if ((flags & FLAG_PADDED) != 0) {
        padLength = Byte.toUnsignedInt(payload[offset]);
        offset += 1;
      }
      int dataLength = payload.length - offset - padLength;
      if (dataLength <= 0) {
        return new byte[]{};
      }
      return Arrays.copyOfRange(payload, offset, offset + dataLength);
    }
  }

  /**
   * State stored for a single HTTP/2 stream.
   */
  private static class Http2StreamState {

    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private final ByteArrayOutputStream headerBlock = new ByteArrayOutputStream();
    private Http2Headers headers;
    private Http2Headers trailers;
    private boolean endStream = false;
    private boolean receivedData = false;
    private boolean headerBlockInProgress = false;

    /**
     * Append DATA bytes to the stream payload.
     *
     * @param bytes the data bytes
     */
    private void appendData(byte[] bytes) {
      receivedData = receivedData || bytes.length > 0;
      data.write(bytes, 0, bytes.length);
    }

    /**
     * Append a header block fragment for later decoding.
     *
     * @param fragment the header block fragment
     */
    private void appendHeaderBlock(byte[] fragment) {
      headerBlockInProgress = true;
      headerBlock.write(fragment, 0, fragment.length);
    }

    /**
     * Consume and reset the current header block buffer.
     *
     * @return the aggregated header block bytes
     */
    private byte[] consumeHeaderBlock() {
      headerBlockInProgress = false;
      var bytes = headerBlock.toByteArray();
      headerBlock.reset();
      return bytes;
    }

    /**
     * Store decoded headers as either initial headers or trailers.
     *
     * @param decodedHeaders the decoded headers
     */
    private void storeHeaders(Http2Headers decodedHeaders) {
      if (headers == null || !receivedData) {
        headers = decodedHeaders;
      } else {
        trailers = decodedHeaders;
      }
    }

    /**
     * Mark the stream as ended.
     */
    private void markEndStream() {
      endStream = true;
    }

    /**
     * Check if a stream has enough information to be emitted as a message.
     *
     * @return true when headers are present and end stream was observed
     */
    private boolean isComplete() {
      return headers != null && endStream;
    }

    /**
     * Check if a header block is currently being assembled.
     *
     * @return true when a header block is in progress
     */
    private boolean isHeaderBlockInProgress() {
      return headerBlockInProgress;
    }

    /**
     * Access decoded headers.
     *
     * @return the decoded headers
     */
    private Http2Headers headers() {
      return headers;
    }

    /**
     * Access decoded trailers.
     *
     * @return the decoded trailers
     */
    private Http2Headers trailers() {
      return trailers;
    }

    /**
     * Access accumulated DATA bytes.
     *
     * @return the DATA payload
     */
    private byte[] data() {
      return data.toByteArray();
    }
  }
}
