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
package de.gematik.rbellogger.facets.grpc;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.facets.http.RbelHttpHeaderFacet;
import de.gematik.rbellogger.facets.http2.RbelHttp2Converter;
import de.gematik.rbellogger.facets.http2.RbelHttp2MessageFacet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Converter parsing gRPC envelopes inside HTTP/2 messages.
 */
@ConverterInfo(dependsOn = {RbelHttp2Converter.class})
public class RbelGrpcConverter extends RbelConverterPlugin {

  private static final List<String> GRPC_CONTENT_TYPES =
      List.of("application/grpc", "application/grpc+proto", "application/grpc+json");
  private static final int GRPC_HEADER_LENGTH = 5;

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
    var http2Facet = rbelElement.getFacet(RbelHttp2MessageFacet.class).orElse(null);
    if (http2Facet == null) {
      return;
    }
    var headerFacet = headerFacet(http2Facet.headers());
    var contentType =
        headerFacet
            .flatMap(f -> headerValue(f, "content-type"))
            .map(value -> value.toLowerCase(Locale.ROOT));
    if (contentType.isEmpty() || !isGrpcContentType(contentType.get())) {
      return;
    }
    var path = headerFacet.flatMap(f -> headerValue(f, ":path")).orElse("");
    var bodyBytes =
        http2Facet.body() == null ? new byte[]{} : http2Facet.body().getRawContent();
    var grpcFrames = parseGrpcFrames(bodyBytes, rbelElement);
    var framesElement = buildFramesElement(rbelElement, converter, grpcFrames);
    var trailerFacet = headerFacet(http2Facet.trailers());
    var status = trailerFacet.flatMap(f -> headerValue(f, "grpc-status")).orElse(null);
    var statusMessage = trailerFacet.flatMap(f -> headerValue(f, "grpc-message")).orElse(null);
    var grpcFacet =
        RbelGrpcMessageFacet.builder()
            .path(RbelElement.wrap(rbelElement, path))
            .contentType(RbelElement.wrap(rbelElement, contentType.get()))
            .frames(framesElement)
            .status(status == null ? new RbelElement(new byte[]{}, rbelElement)
                : RbelElement.wrap(rbelElement, status))
            .statusMessage(
                statusMessage == null
                    ? new RbelElement(new byte[]{}, rbelElement)
                    : RbelElement.wrap(rbelElement, statusMessage))
            .trailers(buildTrailerElement(rbelElement, http2Facet.trailers()))
            .build();
    rbelElement.addFacet(grpcFacet);
  }

  /**
   * Extract the HTTP header facet from a header element.
   *
   * @param headerElement the header element
   * @return the header facet when present
   */
  private Optional<RbelHttpHeaderFacet> headerFacet(RbelElement headerElement) {
    return Optional.ofNullable(headerElement).flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class));
  }

  /**
   * Resolve a header value from a header facet.
   *
   * @param headerFacet the header facet
   * @param name        the header name
   * @return the header value if present
   */
  private Optional<String> headerValue(RbelHttpHeaderFacet headerFacet, String name) {
    return headerFacet
        .getCaseInsensitiveMatches(name)
        .findFirst()
        .map(RbelElement::getRawStringContent);
  }

  /**
   * Check whether a content type belongs to gRPC.
   *
   * @param contentType the content type value
   * @return true when the content type indicates gRPC
   */
  private boolean isGrpcContentType(String contentType) {
    var normalized = contentType.toLowerCase(Locale.ROOT);
    return GRPC_CONTENT_TYPES.stream().anyMatch(normalized::startsWith);
  }

  /**
   * Parse gRPC frames from a payload.
   *
   * @param bodyBytes     the raw message body
   * @param targetElement the element to annotate for errors
   * @return the parsed gRPC frames
   */
  private List<GrpcFrame> parseGrpcFrames(byte[] bodyBytes, RbelElement targetElement) {
    var frames = new ArrayList<GrpcFrame>();
    int offset = 0;
    while (offset + GRPC_HEADER_LENGTH <= bodyBytes.length) {
      boolean compressed = bodyBytes[offset] != 0;
      int length =
          (Byte.toUnsignedInt(bodyBytes[offset + 1]) << 24)
              | (Byte.toUnsignedInt(bodyBytes[offset + 2]) << 16)
              | (Byte.toUnsignedInt(bodyBytes[offset + 3]) << 8)
              | Byte.toUnsignedInt(bodyBytes[offset + 4]);
      offset += GRPC_HEADER_LENGTH;
      if (offset + length > bodyBytes.length) {
        targetElement.addFacet(
            RbelNoteFacet.builder()
                .style(NoteStyling.WARN)
                .value(
                    "Incomplete gRPC frame: expected "
                        + length
                        + " bytes but only "
                        + (bodyBytes.length - offset)
                        + " available.")
                .build());
        break;
      }
      byte[] payload = new byte[length];
      System.arraycopy(bodyBytes, offset, payload, 0, length);
      offset += length;
      frames.add(new GrpcFrame(compressed, length, payload));
    }
    if (offset < bodyBytes.length) {
      targetElement.addFacet(
          RbelNoteFacet.builder()
              .style(NoteStyling.INFO)
              .value("Trailing bytes after gRPC frames: " + (bodyBytes.length - offset))
              .build());
    }
    return frames;
  }

  /**
   * Build the frames element for a list of gRPC frames.
   *
   * @param parent    the parent element
   * @param converter the conversion executor
   * @param frames    the parsed gRPC frames
   * @return the frames element holding a list facet
   */
  private RbelElement buildFramesElement(
      RbelElement parent, RbelConversionExecutor converter, List<GrpcFrame> frames) {
    var framesElement = new RbelElement(new byte[]{}, parent);
    var frameElements = new ArrayList<RbelElement>();
    for (var frame : frames) {
      var frameElement = new RbelElement(new byte[]{}, framesElement);
      var payloadElement = new RbelElement(frame.payload(), frameElement);
      frameElement.addFacet(
          RbelGrpcFrameFacet.builder()
              .compressed(RbelElement.wrap(frameElement, frame.compressed()))
              .length(RbelElement.wrap(frameElement, frame.length()))
              .payload(payloadElement)
              .build());
      if (frame.compressed()) {
        payloadElement.addFacet(
            RbelNoteFacet.builder()
                .style(NoteStyling.WARN)
                .value("gRPC payload is compressed but decompression is not supported.")
                .build());
      }
      converter.convertElement(payloadElement);
      frameElements.add(frameElement);
    }
    framesElement.addFacet(new RbelListFacet(frameElements));
    return framesElement;
  }

  /**
   * Build a trailers element from the HTTP/2 trailers block.
   *
   * @param parent          the parent element
   * @param trailersElement the HTTP/2 trailers element
   * @return the trailers element
   */
  private RbelElement buildTrailerElement(RbelElement parent, RbelElement trailersElement) {
    if (trailersElement == null) {
      return new RbelElement(new byte[]{}, parent);
    }
    var target = new RbelElement(new byte[]{}, parent);
    trailersElement
        .getFacet(RbelHttpHeaderFacet.class)
        .ifPresent(target::addFacet);
    return target;
  }

  /**
   * Parsed gRPC frame record.
   */
  private record GrpcFrame(boolean compressed, int length, byte[] payload) {

  }
}
