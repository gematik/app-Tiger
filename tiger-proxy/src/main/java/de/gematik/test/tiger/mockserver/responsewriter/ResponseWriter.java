/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.responsewriter;

import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.notFoundResponse;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.response;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.cors.CORSHeaders;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public abstract class ResponseWriter {

  protected final Configuration configuration;
  private final CORSHeaders corsHeaders;

  protected ResponseWriter(Configuration configuration) {
    this.configuration = configuration;
    corsHeaders = new CORSHeaders(configuration);
  }

  public void writeResponse(final HttpRequest request, final HttpResponseStatus responseStatus) {
    writeResponse(request, responseStatus, "", "application/json");
  }

  public void writeResponse(
      final HttpRequest request,
      final HttpResponseStatus responseStatus,
      final String body,
      final String contentType) {
    HttpResponse response =
        response()
            .withStatusCode(responseStatus.code())
            .withReasonPhrase(responseStatus.reasonPhrase())
            .withBody(body);
    if (body != null && !body.isEmpty()) {
      response.replaceHeader(header(CONTENT_TYPE.toString(), contentType + "; charset=utf-8"));
    }
    writeResponse(request, response, true);
  }

  public void writeResponse(
      final HttpRequest request, HttpResponse response, final boolean apiResponse) {
    if (response == null) {
      response = notFoundResponse();
    }
    if (configuration.enableCORSForAllResponses()) {
      corsHeaders.addCORSHeaders(request, response);
    } else if (apiResponse && configuration.enableCORSForAPI()) {
      corsHeaders.addCORSHeaders(request, response);
    }
    String contentLengthHeader = response.getFirstHeader(CONTENT_LENGTH.toString());
    if (isNotBlank(contentLengthHeader)) {
      try {
        int contentLength = Integer.parseInt(contentLengthHeader);
        if (response.getBodyAsRawBytes().length > contentLength) {
          log.info(
              "returning response with content-length header {} which is smaller then response body length {}, body will likely be truncated by client receiving request",
              contentLength,
              response.getBodyAsRawBytes().length);
        }
      } catch (NumberFormatException ignore) {
        // ignore exception while parsing invalid content-length header
      }
    }

    // send response down the request HTTP2 stream
    if (request.getStreamId() != null) {
      response.withStreamId(request.getStreamId());
    }

    sendResponse(request, addConnectionHeader(request, response));
  }

  public abstract void sendResponse(HttpRequest request, HttpResponse response);

  protected HttpResponse addConnectionHeader(
      final HttpRequest request, final HttpResponse response) {
    HttpResponse responseWithConnectionHeader = response.clone();

    if (Boolean.TRUE.equals(request.getKeepAlive())) {
      responseWithConnectionHeader.replaceHeader(
          header(CONNECTION.toString(), KEEP_ALIVE.toString()));
    } else {
      responseWithConnectionHeader.replaceHeader(header(CONNECTION.toString(), CLOSE.toString()));
    }

    return responseWithConnectionHeader;
  }
}
