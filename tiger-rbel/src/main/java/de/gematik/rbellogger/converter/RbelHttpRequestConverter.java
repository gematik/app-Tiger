/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter;

import static com.google.common.primitives.Bytes.indexOf;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RbelHttpRequestConverter extends RbelHttpResponseConverter {

  @Override
  public boolean ignoreOversize() {
    return true;
  }

  @Override
  public void consumeElement(final RbelElement targetElement, final RbelConverter converter) {
    final String content = targetElement.getRawStringContent();
    if (StringUtils.isEmpty(content) || !content.contains("\n")) {
      return;
    }
    String eol = findEolInHttpMessage(content);
    if (content.split(eol).length == 0) {
      return;
    }
    String firstLine = content.split(eol)[0].trim();
    if (!((firstLine.startsWith("GET ")
            || firstLine.startsWith("POST ")
            || firstLine.startsWith("PUT ")
            || firstLine.startsWith("DELETE "))
        && (firstLine.endsWith("HTTP/1.0")
            || firstLine.endsWith("HTTP/1.1")
            || firstLine.endsWith("HTTP/2.0")))) {
      return;
    }
    int endOfHeadIndex = indexOf(targetElement.getRawContent(), (eol + eol).getBytes());
    if (endOfHeadIndex < 0) {
      endOfHeadIndex = content.length();
    }
    String messageHeader = content.substring(0, endOfHeadIndex);
    final int space = messageHeader.indexOf(" ");
    final int space2 = messageHeader.indexOf(" ", space + 1);
    final String method = messageHeader.substring(0, space);
    final String path = messageHeader.substring(space + 1, space2);

    final RbelElement headerElement = extractHeaderFromMessage(targetElement, converter, eol);

    final RbelElement pathElement = converter.convertElement(path, targetElement);
    if (!pathElement.hasFacet(RbelUriFacet.class)) {
      throw new RbelConversionException("Encountered ill-formatted path: " + path);
    }

    final byte[] bodyData =
        extractBodyData(
            targetElement,
            endOfHeadIndex + 2 * eol.length(),
            headerElement.getFacetOrFail(RbelHttpHeaderFacet.class),
            eol);
    final RbelElement bodyElement =
        new RbelElement(
            bodyData,
            targetElement,
            findCharsetInHeader(headerElement.getFacetOrFail(RbelHttpHeaderFacet.class)));

    final RbelHttpRequestFacet httpRequest =
        RbelHttpRequestFacet.builder()
            .method(converter.convertElement(method, targetElement))
            .path(pathElement)
            .build();
    targetElement.addFacet(httpRequest);
    targetElement.addFacet(new RbelRequestFacet(method + " " + path));
    targetElement.addFacet(
        RbelHttpMessageFacet.builder().header(headerElement).body(bodyElement).build());
    converter.convertElement(bodyElement);
  }

  public static String findEolInHttpMessage(String content) {
    if (content.contains("\r\n") && content.indexOf("\r\n") < content.indexOf("\n")) {
      return "\r\n";
    }
    return "\n";
  }
}
