/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
