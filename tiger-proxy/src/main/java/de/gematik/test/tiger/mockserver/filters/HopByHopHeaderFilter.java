package de.gematik.test.tiger.mockserver.filters;

import de.gematik.test.tiger.mockserver.model.Header;
import de.gematik.test.tiger.mockserver.model.Headers;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class HopByHopHeaderFilter {

  private static final List<String> requestHeadersToRemove =
      Arrays.asList(
          "proxy-connection",
          "connection",
          "keep-alive",
          "transfer-encoding",
          "te",
          "trailer",
          "proxy-authorization",
          "proxy-authenticate",
          "upgrade");

  private static final List<String> responseHeadersToRemove =
      Arrays.asList(
          "proxy-connection",
          "connection",
          "keep-alive",
          "transfer-encoding",
          "content-length",
          "te",
          "trailer",
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

  public HttpResponse onResponse(HttpResponse response) {
    if (response != null) {
      Headers headers = new Headers();
      for (Header header : response.getHeaderList()) {
        if (!responseHeadersToRemove.contains(header.getName().toLowerCase(Locale.ENGLISH))) {
          headers.withEntry(header);
        }
      }
      HttpResponse clonedResponse = response.clone();
      if (!headers.isEmpty()) {
        clonedResponse.withHeaders(headers);
      }
      return clonedResponse;
    } else {
      return null;
    }
  }
}
