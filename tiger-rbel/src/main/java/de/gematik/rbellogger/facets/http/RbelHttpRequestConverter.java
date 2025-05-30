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

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RbelHttpRequestConverter extends RbelHttpResponseConverter {

  private static final Set<String> HTTP_METHODS =
      Set.of("GET", "POST", "PUT", "HEAD", "OPTIONS", "PATCH", "DELETE", "TRACE", "CONNECT");
  private static final byte[] HTTP_11_BYTES = "HTTP/1.1".getBytes();

  public RbelHttpRequestConverter(RbelConfiguration configuration) {
    super(configuration);
  }

  @Override
  public void consumeElement(
      final RbelElement targetElement, final RbelConversionExecutor converter) {
    var content = targetElement.getContent();
    if (!startsWithHttpVerb(content)) {
      return;
    }
    var eolOpt = findEolInHttpMessage(content);
    if (eolOpt.isEmpty()) {
      return;
    }
    var eol = eolOpt.get();
    var firstLineOpt = extractFirstLine(targetElement, eol, content);
    if (firstLineOpt.isEmpty()) {
      return;
    }
    checkEolValue(eol, targetElement);
    int endOfHeadIndex = findEndOfHeadIndex(content, eol);

    var firstLineParts = firstLineOpt.get();
    var path = firstLineParts.path;
    var method = firstLineParts.method;
    var httpVersion = firstLineParts.version;

    final RbelElement pathElement = converter.convertElement(path, targetElement);

    var stringContent = targetElement.getRawStringContent();
    if (stringContent == null) {
      return;
    }

    final RbelElement headerElement =
        extractHeaderFromMessage(targetElement, converter, eol, stringContent);
    RbelHttpHeaderFacet httpHeader = headerElement.getFacetOrFail(RbelHttpHeaderFacet.class);
    verifyHeader(httpHeader, httpVersion, targetElement);

    final byte[] bodyData =
        extractBodyData(targetElement, endOfHeadIndex + 2 * eol.length(), httpHeader, eol);
    final RbelElement bodyElement =
        new RbelElement(bodyData, targetElement, findCharsetInHeader(httpHeader));

    final RbelHttpRequestFacet httpRequest =
        RbelHttpRequestFacet.builder()
            .method(converter.convertElement(method, targetElement))
            .path(pathElement)
            .build();
    targetElement.addFacet(httpRequest);
    targetElement.addFacet(new RbelRequestFacet(method + " " + path, true));
    targetElement.addFacet(
        RbelHttpMessageFacet.builder()
            .header(headerElement)
            .body(bodyElement)
            .httpVersion(httpVersion)
            .build());
    converter.convertElement(bodyElement);
  }

  private void verifyHeader(
      RbelHttpHeaderFacet httpHeader, RbelElement httpVersion, RbelElement targetElement) {
    if (httpVersion.getContent().startsWith(HTTP_11_BYTES)) {
      if (httpHeader.getCaseInsensitiveMatches("host").findAny().isEmpty()) {
        targetElement.addFacet(
            RbelNoteFacet.builder()
                .value("HTTP/1.1 request does not contain Host header")
                .style(styleParsingError(targetElement))
                .build());
        if (!isLenientParsingMode() && isTcpMessage(targetElement)) {
          throw new RbelConversionException("HTTP/1.1 request does not contain Host header");
        }
      }
    }
  }

  @Data
  @Builder
  private static class RequestFirstLineParts {
    String method;
    String path;
    RbelElement version;
  }

  private static Optional<RequestFirstLineParts> extractFirstLine(
      RbelElement targetElement, String eol, RbelContent content) {
    var firstEndLineIndex = content.indexOf(eol.getBytes());

    String firstLine =
        new String(content.toByteArray(0, firstEndLineIndex), targetElement.getElementCharset());

    final String[] firstLineParts = StringUtils.split(firstLine, " ", 3);

    if (firstLineParts.length != 3) {
      return Optional.empty();
    }

    if (!(firstLineParts[2].startsWith("HTTP/"))) {
      return Optional.empty();
    }

    val method = firstLineParts[0];
    val path = firstLineParts[1];
    val httpVersion = new RbelElement(firstLineParts[2].getBytes(), targetElement);
    return Optional.of(
        RequestFirstLineParts.builder().method(method).path(path).version(httpVersion).build());
  }

  private static int findEndOfHeadIndex(RbelContent content, String eol) {
    int endOfHeadIndex = content.indexOf((eol + eol).getBytes());
    if (endOfHeadIndex < 0) {
      endOfHeadIndex = content.size();
    }
    return endOfHeadIndex;
  }

  public boolean startsWithHttpVerb(RbelContent data) {
    if (data.isEmpty()) {
      return false;
    }
    String firstLine =
        new String(data.toByteArray(0, Math.min(8, data.size())), StandardCharsets.US_ASCII);
    String method = firstLine.split(" ", 2)[0];
    return HTTP_METHODS.contains(method)
        && data.size() > method.length()
        && data.get(method.length()) == ' ';
  }
}
