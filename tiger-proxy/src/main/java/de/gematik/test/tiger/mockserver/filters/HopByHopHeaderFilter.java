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

package de.gematik.test.tiger.mockserver.filters;

import de.gematik.test.tiger.mockserver.model.Header;
import de.gematik.test.tiger.mockserver.model.Headers;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

  public HttpRequest onRequest(HttpRequest request) {
    if (request != null) {
      Headers headers = new Headers();
      for (Header header : request.getHeaderList()) {
        if (!requestHeadersToRemove.contains(header.getName().toLowerCase(Locale.ENGLISH))) {
          headers.withEntry(header);
        }
      }
      HttpRequest clonedRequest = request.clone();
      if (!headers.isEmpty()) {
        clonedRequest.withHeaders(headers);
      }
      return clonedRequest;
    } else {
      return null;
    }
  }
}
