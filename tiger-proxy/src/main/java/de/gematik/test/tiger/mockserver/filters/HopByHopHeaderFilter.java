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
package de.gematik.test.tiger.mockserver.filters;

import de.gematik.test.tiger.mockserver.model.Header;
import de.gematik.test.tiger.mockserver.model.Headers;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.val;

/*
 * @author jamesdbloom
 */
public class HopByHopHeaderFilter {

  private static final List<String> requestHeadersToRemove =
      Arrays.asList(
          "proxy-connection",
          "keep-alive",
          "transfer-encoding",
          "te",
          "connection",
          "trailer",
          "proxy-authorization",
          "proxy-authenticate",
          "upgrade");

  private static final List<String> websocketHandshakeHeaders = List.of("connection", "upgrade");

  public HttpRequest onRequest(HttpRequest request) {
    if (request != null) {
      val isWebsocketHandshakeRequest = request.isWebsocketHandshake();
      Headers headers = new Headers();
      for (Header header : request.getHeaderList()) {
        val lowerCaseHeaderName = header.getName().toLowerCase(Locale.ENGLISH);
        if (!requestHeadersToRemove.contains(lowerCaseHeaderName)
            || (isWebsocketHandshakeRequest
                && websocketHandshakeHeaders.contains(lowerCaseHeaderName))) {
          headers.withEntry(header);
        }
      }
      if (!headers.isEmpty()) {
        request.withHeaders(headers);
      }
      return request;
    } else {
      return null;
    }
  }
}
