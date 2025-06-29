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
package de.gematik.test.tiger.mockserver.mappers;

import de.gematik.test.tiger.mockserver.codec.BodyDecoderEncoder;
import de.gematik.test.tiger.mockserver.model.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class FullHttpResponseToMockServerHttpResponse {

  private final BodyDecoderEncoder bodyDecoderEncoder = new BodyDecoderEncoder();

  public HttpResponse mapFullHttpResponseToMockServerResponse(FullHttpResponse fullHttpResponse) {
    HttpResponse httpResponse = new HttpResponse();
    try {
      if (fullHttpResponse != null) {
        if (fullHttpResponse.decoderResult().isFailure()) {
          log.error("exception decoding response ", fullHttpResponse.decoderResult().cause());
        }
        setStatusCode(httpResponse, fullHttpResponse);
        setHeaders(httpResponse, fullHttpResponse);
        setBody(httpResponse, fullHttpResponse);
      }
    } catch (RuntimeException e) {
      log.error("exception decoding response {}", fullHttpResponse, e);
    }
    return httpResponse;
  }

  private void setStatusCode(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
    HttpResponseStatus status = fullHttpResponse.status();
    httpResponse.withStatusCode(status.code());
    httpResponse.withReasonPhrase(status.reasonPhrase());
  }

  private void setHeaders(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
    Set<String> headerNames = fullHttpResponse.headers().names();
    if (!headerNames.isEmpty()) {
      Headers headers = new Headers();
      for (String headerName : headerNames) {
        headers.withEntry(headerName, fullHttpResponse.headers().getAll(headerName));
      }
      httpResponse.withHeaders(headers);
    }
  }

  private void setBody(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
    httpResponse.withBody(bodyDecoderEncoder.byteBufToBody(fullHttpResponse.content()));
  }
}
