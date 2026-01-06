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
package de.gematik.rbellogger.facets.websocket;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@ConverterInfo(
    dependsOn = {RbelHttpResponseConverter.class},
    onlyActivateFor = "websocket")
public class RbelStompConverter extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    // only root TCP/IP messages are considered for websocket conversion
    if (rbelElement.getParentNode() == null) {
      return;
    }
    parseStompMessage(rbelElement, converter);
  }

  private void parseStompMessage(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (rbelElement.getContent().endsWith(new byte[] {0x00})) {
      val firstLinebreak = rbelElement.getContent().indexOf("\n".getBytes());
      if (firstLinebreak == -1) {
        return;
      }
      val command = rbelElement.getContent().subArray(0, firstLinebreak);

      if (!RbelStompFrameType.isCommand(command)) {
        return;
      }

      // Stomp Command detected
      final String[] parts = rbelElement.getRawStringContent().split("\n");

      final List<String> headers;
      if (parts.length > 3) {
        headers = Arrays.asList(parts).subList(1, parts.length - 2);
      } else {
        headers = Collections.emptyList();
      }

      final String body;
      if (parts.length == 0) {
        body = "";
      } else {
        String last = parts[parts.length - 1];
        while (!last.isEmpty() && last.charAt(last.length() - 1) == '\0') {
          last = last.substring(0, last.length() - 1);
        }
        body = last;
      }

      val commandElement = RbelElement.create(command, rbelElement);
      val headersElement = new RbelElement(new byte[] {}, rbelElement);
      val headersFacet = new RbelStompHeadersFacet();
      for (String headerLine : headers) {
        String[] headerParts = headerLine.split(":");
        headersFacet.put(
            headerParts[0],
            converter.convertElement(headerParts.length > 1 ? headerParts[1] : "", headersElement));
      }
      headersElement.addFacet(headersFacet);
      val bodyElement =
          new RbelElement(body.getBytes(rbelElement.getElementCharset()), rbelElement);
      val stompFacet = new RbelStompFacet(commandElement, headersElement, bodyElement);

      rbelElement.addFacet(stompFacet);
      converter.convertElement(commandElement);
      converter.convertElement(bodyElement);
    }
  }
}
