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
package de.gematik.rbellogger.facets.http;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LENIENT_HTTP_PARSING;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.net.MediaType;
import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.util.RbelContent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class RbelHttpResponseConverter extends RbelConverterPlugin {

  static final String CRLF = "\r\n";
  private static final byte[] CRLF_BYTES = CRLF.getBytes(UTF_8);
  private static final String HTTP_PREFIX = "HTTP/";
  private static final byte[] HTTP_PREFIX_BYTES = HTTP_PREFIX.getBytes(UTF_8);
  @Getter private final boolean lenientParsingMode;

  public static final Map<String, RbelHttpCodingConverter> HTTP_CODINGS_MAP =
      Map.of(
          "chunked", RbelHttpResponseConverter::decodeChunked,
          "deflate", RbelHttpResponseConverter::decodeDeflate,
          "gzip", RbelHttpResponseConverter::decodeGzip);

  private static byte[] decodeGzip(byte[] bytes, String eol, Charset charset) {
    log.atTrace().log(() -> "Decoding data with gzip");
    try (final InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
      return inputStream.readAllBytes();
    } catch (Exception e) {
      throw new RbelConversionException("Error while decoding gzip content", e);
    }
  }

  private static byte[] decodeDeflate(byte[] bytes, String eol, Charset charset) {
    log.atTrace().log(() -> "Decoding data with deflate");
    try (final InputStream inputStream = new InflaterInputStream(new ByteArrayInputStream(bytes))) {
      return inputStream.readAllBytes();
    } catch (Exception e) {
      throw new RbelConversionException("Error while decoding gzip content", e);
    }
  }

  private static byte[] decodeChunked(byte[] inputData, String eol, Charset charset) {
    log.atTrace().log(() -> "Decoding data with chunked encoding");
    int chunkSeparator = new String(inputData, charset).indexOf(eol) + eol.length();

    final int indexOfChunkTerminator =
        determineEndOfChunkedBody(RbelContent.of(inputData), 0, eol) - 7;
    if (indexOfChunkTerminator >= 0) {
      return Arrays.copyOfRange(
          inputData, Math.min(inputData.length, chunkSeparator), indexOfChunkTerminator);
    } else {
      throw new RbelConversionException(
          "Detected incorrect use of chunked encoding: Chunked was given as"
              + " transfer-encoding, but the chunk-separator could not be found in the"
              + " content. Message will not be parsed.");
    }
  }

  public RbelHttpResponseConverter(RbelConfiguration configuration) {
    this.lenientParsingMode =
        Optional.ofNullable(configuration.getLenientHttpParsing())
            .orElseGet(LENIENT_HTTP_PARSING::getValueOrDefault);
  }

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public boolean skipParsingOversizedContent() {
    return true;
  }

  @Override
  public void consumeElement(RbelElement targetElement, final RbelConversionExecutor converter) {
    var content = targetElement.getContent();
    if (!content.startsWith(HTTP_PREFIX_BYTES)) {
      return;
    }

    new Parser(targetElement, converter, content).parse();
  }

  @AllArgsConstructor
  private class Parser {
    private final RbelElement targetElement;
    private final RbelConversionExecutor converter;
    private final RbelContent content;

    private void parse() {
      var eolOpt = findEolInHttpMessage(content);
      if (eolOpt.isEmpty()) {
        return;
      }
      var eol = eolOpt.get();
      checkEolValue(eol, targetElement);
      var endOfHeaderIndexOpt = findEndOfHeaderIndex(eol);
      if (endOfHeaderIndexOpt.isEmpty()) {
        if (lenientParsingMode || !isTcpMessage(targetElement)) {
          endOfHeaderIndexOpt = Optional.of(content.size());
        } else {
          targetElement.addFacet(
              RbelNoteFacet.builder()
                  .style(NoteStyling.WARN)
                  .value(
                      "Unable to determine end of HTTP header. Does the header end with double"
                          + " CRLF?")
                  .build());
          return;
        }
      }
      var endOfHeaderIndex = endOfHeaderIndexOpt.get();
      var stringContent = targetElement.getRawStringContent();
      if (stringContent == null) {
        return;
      }

      RbelElement headerElement =
          extractHeaderFromMessage(targetElement, converter, eol, stringContent);
      RbelHttpHeaderFacet httpHeaderFacet = headerElement.getFacetOrFail(RbelHttpHeaderFacet.class);
      final byte[] rawBodyData =
          extractBodyData(targetElement, endOfHeaderIndex, httpHeaderFacet, eol);
      final RbelElement bodyElement =
          new RbelElement(rawBodyData, targetElement, findCharsetInHeader(httpHeaderFacet));
      final RbelElement responseCode = extractResponseCodeFromMessage(stringContent);
      final RbelHttpResponseFacet rbelHttpResponse =
          RbelHttpResponseFacet.builder()
              .responseCode(responseCode)
              .reasonPhrase(extractReasonPhraseFromMessage(stringContent))
              .build();

      targetElement.addFacet(rbelHttpResponse);
      targetElement.addFacet(new RbelResponseFacet(responseCode.getRawStringContent()));
      final var httpVersion =
          new RbelElement(
              stringContent.substring(0, stringContent.indexOf(" ")).getBytes(), targetElement);
      val httpMessageFacet =
          RbelHttpMessageFacet.builder()
              .header(headerElement)
              .body(bodyElement)
              .httpVersion(httpVersion)
              .build();
      targetElement.addFacet(httpMessageFacet);
      targetElement.addFacet(new RbelRootFacet<>(httpMessageFacet));

      converter.convertElement(bodyElement);
    }

    private Optional<Integer> findEndOfHeaderIndex(String eol) {
      int endOfHeadIndex = content.indexOf((eol + eol).getBytes());
      if (endOfHeadIndex == -1) {
        return Optional.empty();
      }
      endOfHeadIndex += 2 * eol.length();
      return Optional.of(endOfHeadIndex);
    }

    private RbelElement extractResponseCodeFromMessage(String content) {
      return RbelElement.builder()
          .parentNode(targetElement)
          .rawContent(content.split("\\s")[1].getBytes(UTF_8))
          .build();
    }

    private RbelElement extractReasonPhraseFromMessage(String content) {
      String[] responseStatusLineSplit = content.split("\\r\\n")[0].trim().split("\\s", 3);
      if (responseStatusLineSplit.length == 2) {
        return RbelElement.builder().parentNode(targetElement).build();
      } else {
        return RbelElement.builder()
            .parentNode(targetElement)
            .rawContent(
                responseStatusLineSplit[2].trim().getBytes(targetElement.getElementCharset()))
            .build();
      }
    }
  }

  public RbelElement extractHeaderFromMessage(
      RbelElement rbel, RbelConversionExecutor converter, String eol, String content) {
    int endOfBodyPosition = content.indexOf(eol + eol);
    int endOfFirstLine = content.indexOf(eol) + eol.length();

    if (endOfBodyPosition < 0) {
      endOfBodyPosition = content.length();
    } else {
      endOfBodyPosition += 2 * eol.length();
    }

    final List<String> headerList =
        Arrays.stream(content.substring(endOfFirstLine, endOfBodyPosition).split(eol))
            .filter(line -> !line.isEmpty() && !line.startsWith(HTTP_PREFIX))
            .toList();

    RbelElement headerElement =
        new RbelElement(String.join(eol, headerList).getBytes(rbel.getElementCharset()), rbel);
    final RbelMultiMap<RbelElement> headerMap =
        headerList.stream()
            .map(line -> parseStringToKeyValuePair(line, converter, headerElement))
            .collect(RbelMultiMap.COLLECTOR);
    headerElement.addFacet(new RbelHttpHeaderFacet(headerMap));

    return headerElement;
  }

  private Optional<Charset> strictParsingOfCharset(String s) {
    try {
      return Optional.ofNullable(s)
          .map(MediaType::parse)
          .map(MediaType::charset)
          .filter(com.google.common.base.Optional::isPresent)
          .map(com.google.common.base.Optional::get);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  Optional<Charset> findCharsetInHeader(RbelHttpHeaderFacet headerMap) {
    return headerMap
        .getCaseInsensitiveMatches("Content-Type")
        .map(RbelElement::getRawStringContent)
        .filter(Objects::nonNull)
        .map(str -> strictParsingOfCharset(str).orElse(guessCharset(str)))
        .findFirst();
  }

  private Charset guessCharset(String str) {
    var lowerCaseString = str.toLowerCase();
    return Stream.of(
            UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII, StandardCharsets.UTF_16)
        .filter(
            charset ->
                charset.aliases().stream()
                    .map(String::toLowerCase)
                    .anyMatch(lowerCaseString::contains))
        .findFirst()
        .orElse(StandardCharsets.UTF_8);
  }

  protected SimpleImmutableEntry<String, RbelElement> parseStringToKeyValuePair(
      final String line, final RbelConversionExecutor context, RbelElement headerElement) {
    final int colon = line.indexOf(':');
    if (colon == -1) {
      throw new IllegalArgumentException("Header malformed: '" + line + "'");
    }
    val key = line.substring(0, colon).trim();
    val value = line.substring(colon + 1).trim();
    Charset elementCharset = headerElement.getElementCharset();
    val rbelElement =
        context.convertElement(new RbelElement(value.getBytes(elementCharset), headerElement));

    if (value.contains(",")) {
      val childNodes = new ArrayList<RbelElement>();
      rbelElement.addFacet(new RbelListFacet(childNodes));
      for (String part : value.split(",")) {
        childNodes.add(
            context.convertElement(
                new RbelElement(part.trim().getBytes(elementCharset), rbelElement)));
      }
    }

    return new SimpleImmutableEntry<>(key, rbelElement);
  }

  public byte[] applyCodings(
      final byte[] inputData,
      final RbelHttpHeaderFacet headerMap,
      final String eol,
      Charset charset,
      String codingKey) {
    final List<RbelHttpCodingConverter> codingConverters =
        headerMap
            .getCaseInsensitiveMatches(codingKey)
            .map(RbelElement::getRawStringContent)
            .filter(Objects::nonNull)
            .map(s -> s.split(","))
            .flatMap(Arrays::stream)
            .map(String::toLowerCase)
            .map(String::trim)
            .map(
                encoding -> {
                  if (!HTTP_CODINGS_MAP.containsKey(encoding)) {
                    throw new RbelConversionException(
                        "Unsupported encoding found in HTTP header: " + encoding);
                  }
                  log.atTrace().addArgument(() -> encoding).log("Adding decoder for encoding: {}");
                  return HTTP_CODINGS_MAP.get(encoding);
                })
            .toList();

    byte[] data = inputData;

    for (RbelHttpCodingConverter codingConverter : codingConverters) {
      data = codingConverter.decode(data, eol, charset);
    }

    return data;
  }

  public byte[] extractBodyData(
      RbelElement targetElement,
      int bodyDataStartOffset,
      final RbelHttpHeaderFacet headerMap,
      final String eol) {
    val contentEndIndex =
        calculateContentLength(targetElement, bodyDataStartOffset, headerMap, eol);
    var content = targetElement.getContent();
    if (contentEndIndex > content.size()) {
      throw new RbelConversionException("Hit end of messages while trying to determine body size.");
    }
    byte[] inputData =
        content.toByteArray(Math.min(bodyDataStartOffset, contentEndIndex), contentEndIndex);

    Charset elementCharset = targetElement.getElementCharset();
    return applyCodings(
        applyCodings(inputData, headerMap, eol, elementCharset, "Content-Encoding"),
        headerMap,
        eol,
        elementCharset,
        "Transfer-Encoding");
  }

  private int calculateContentLength(
      final RbelElement targetElement,
      final int bodyDataStartOffset,
      final RbelHttpHeaderFacet headerMap,
      String eol) {
    final RbelContent content = targetElement.getContent();
    val messageBodyIsPresent = bodyDataStartOffset < content.size();

    if (messageBodyIsPresent) {
      return parseBody(targetElement, bodyDataStartOffset, headerMap, eol, content);
    }

    if (bodyDataStartOffset == content.size()) {
      return bodyDataStartOffset;
    }

    if (!lenientParsingMode) {
      targetElement.addFacet(
          RbelNoteFacet.builder()
              .style(styleParsingError(targetElement))
              .value(
                  "No body found in HTTP message (Does the message terminate with correct line"
                      + " breaks?)")
              .build());
      if (isTcpMessage(targetElement)) {
        throw new RbelConversionException("No body found in HTTP message");
      }
    }
    return content.size();
  }

  private int parseBody(
      RbelElement targetElement,
      int bodyDataStartOffset,
      RbelHttpHeaderFacet headerMap,
      String eol,
      RbelContent content) {
    final Optional<RbelElement> contentLengthHeader =
        headerMap.getCaseInsensitiveMatches("Content-Length").findAny();
    final Optional<RbelElement> transferEncodingHeader =
        headerMap.getCaseInsensitiveMatches("Transfer-Encoding").findAny();
    if (contentLengthHeader.isPresent()) {
      final int endOfBodyIndex =
          bodyDataStartOffset + parseContentLengthHeader(contentLengthHeader.get());
      if (endOfBodyIndex > content.size()) {
        throw new RbelConversionException(
            "Body-length exceeds available content (Wanted "
                + endOfBodyIndex
                + " bytes, but only "
                + content.size()
                + " bytes available)");
      }
      targetElement.setUsedBytes(endOfBodyIndex);
      return endOfBodyIndex;
    } else if (transferEncodingHeader.isPresent()) {
      final int endOfChunkedBody = determineEndOfChunkedBody(content, bodyDataStartOffset, eol);
      targetElement.setUsedBytes(endOfChunkedBody);
      return endOfChunkedBody;
    } else {
      targetElement.setUsedBytes(bodyDataStartOffset);
      return bodyDataStartOffset;
    }
  }

  private static int parseContentLengthHeader(RbelElement contentLengthHeader) {
    return Optional.ofNullable(contentLengthHeader.getRawStringContent())
        .map(Integer::parseInt)
        .orElseThrow();
  }

  private static int determineEndOfChunkedBody(
      RbelContent content, int bodyDataStartOffset, String eol) {
    final byte[] bytes = eol.getBytes();
    int endOfChunkedBody = bodyDataStartOffset;
    int currentChunkSize;
    do {
      byte[] currentChunkSizeData =
          content.toByteArray(endOfChunkedBody, content.indexOf(bytes, endOfChunkedBody));
      try {
        currentChunkSize = HexFormat.fromHexDigits(new String(currentChunkSizeData));
      } catch (NumberFormatException e) {
        throw new RbelConversionException(
            "Illegal chunked-encoding detected: '"
                + new String(currentChunkSizeData)
                + "' is not a valid integer",
            e);
      }
      endOfChunkedBody += currentChunkSizeData.length + 2 + currentChunkSize + 2;
    } while (currentChunkSize > 0);
    return endOfChunkedBody;
  }

  NoteStyling styleParsingError(RbelElement targetElement) {
    return isTcpMessage(targetElement) ? NoteStyling.WARN : NoteStyling.INFO;
  }

  boolean isTcpMessage(RbelElement targetElement) {
    return targetElement.hasFacet(RbelTcpIpMessageFacet.class);
  }

  public Optional<String> findEolInHttpMessage(RbelContent content) {
    if (content.indexOf(CRLF_BYTES) >= 0) {
      return Optional.of(CRLF);
    } else if (content.indexOf((byte) '\n') >= 0) {
      return Optional.of("\n");
    } else {
      return Optional.empty();
    }
  }

  void checkEolValue(String eol, RbelElement targetElement) {
    if (!eol.equals(CRLF)) {
      targetElement.addFacet(
          RbelNoteFacet.builder()
              .style(NoteStyling.INFO)
              .value(
                  "Non-standard line endings detected. Expected CRLF, but found: "
                      + Hex.toHexString(eol.getBytes()))
              .build());
    }
  }
}
