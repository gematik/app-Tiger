/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.converter;

import static com.google.common.primitives.Bytes.indexOf;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RbelHttpRequestConverter extends RbelHttpResponseConverter {

  private static final Set<String> HTTP_METHODS =
      Set.of("GET", "POST", "PUT", "HEAD", "OPTIONS", "PATCH", "DELETE", "TRACE", "CONNECT");

  @Override
  public void consumeElement(final RbelElement targetElement, final RbelConverter converter) {
    byte[] rawContent = targetElement.getRawContent();
    if (!startsWithHttpVerb(rawContent)) {
      return;
    }
    var eolOpt = findEolInHttpMessage(rawContent);
    if (eolOpt.isEmpty()) {
      return;
    }
    var eol = eolOpt.get();
    var firstLineOpt = extractFirstLine(targetElement, eol, rawContent);
    if (firstLineOpt.isEmpty()) {
      return;
    }
    int endOfHeadIndex = findEndOfHeadIndex(rawContent, eol);
    var content = targetElement.getRawStringContent();
    if (content == null) {
      return;
    }

    var firstLineParts = firstLineOpt.get();
    var path = firstLineParts.path;
    var method = firstLineParts.method;
    var httpVersion = firstLineParts.version;

    final RbelElement pathElement = converter.convertElement(path, targetElement);
    if (!pathElement.hasFacet(RbelUriFacet.class)) {
      throw new RbelConversionException("Encountered ill-formatted path: " + path);
    }

    final RbelElement headerElement =
        extractHeaderFromMessage(targetElement, converter, eol, content);
    RbelHttpHeaderFacet httpHeader = headerElement.getFacetOrFail(RbelHttpHeaderFacet.class);

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
    targetElement.addFacet(
        RbelRequestFacet.builder()
            .responseRequired(true)
            .menuInfoString(method + " " + path)
            .build());
    targetElement.addFacet(
        RbelHttpMessageFacet.builder()
            .header(headerElement)
            .body(bodyElement)
            .httpVersion(httpVersion)
            .build());
    converter.convertElement(bodyElement);
  }

  @Data
  @Builder
  private static class RequestFirstLineParts {
    String method;
    String path;
    RbelElement version;
  }

  private static Optional<RequestFirstLineParts> extractFirstLine(
      RbelElement targetElement, String eol, byte[] rawContent) {
    var firstEndLineIndex = indexOf(rawContent, eol.getBytes());

    String firstLine =
        new String(rawContent, 0, firstEndLineIndex, targetElement.getElementCharset());

    final String[] firstLineParts = StringUtils.split(firstLine, " ", 3);

    if (firstLineParts.length != 3) {
      return Optional.empty();
    }

    if (!(firstLineParts[2].startsWith("HTTP/"))) {
      return Optional.empty();
    }

    final String method = firstLineParts[0];
    final String path = firstLineParts[1];
    final var httpVersion = new RbelElement(firstLineParts[2].getBytes(), targetElement);
    return Optional.of(
        RequestFirstLineParts.builder().method(method).path(path).version(httpVersion).build());
  }

  private static int findEndOfHeadIndex(byte[] rawContent, String eol) {
    int endOfHeadIndex = indexOf(rawContent, (eol + eol).getBytes());
    if (endOfHeadIndex < 0) {
      endOfHeadIndex = rawContent.length;
    }
    return endOfHeadIndex;
  }

  public boolean startsWithHttpVerb(byte[] data) {
    if (ArrayUtils.isEmpty(data)) {
      return false;
    }
    String firstLine =
        new String(
            ArrayUtils.subarray(data, 0, Math.min(8, data.length)), StandardCharsets.US_ASCII);
    String method = firstLine.split(" ", 2)[0];
    return HTTP_METHODS.contains(method)
        && data.length > method.length()
        && data[method.length()] == ' ';
  }
}
