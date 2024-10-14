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

package de.gematik.test.tiger.mockserver.mappers;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.codec.BodyDecoderEncoder;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpProtocol;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.HttpConversionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class MockServerHttpRequestToFullHttpRequest {

  private final Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations;
  private final BodyDecoderEncoder bodyDecoderEncoder;

  public MockServerHttpRequestToFullHttpRequest(
      Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
    this.proxyConfigurations = proxyConfigurations;
    this.bodyDecoderEncoder = new BodyDecoderEncoder();
  }

  public FullHttpRequest mapMockServerRequestToNettyRequest(HttpRequest httpRequest) {
    // method
    HttpMethod httpMethod = HttpMethod.valueOf(httpRequest.getMethodOrDefault("GET"));
    try {
      // the request
      FullHttpRequest request =
          new DefaultFullHttpRequest(
              HttpVersion.HTTP_1_1,
              httpMethod,
              getURI(httpRequest, proxyConfigurations),
              getBody(httpRequest));

      // headers
      setHeader(httpRequest, request);

      return request;
    } catch (RuntimeException throwable) {
      log.error("exception encoding request {}", httpRequest, throwable);
      return new DefaultFullHttpRequest(
          HttpVersion.HTTP_1_1, httpMethod, getURI(httpRequest, proxyConfigurations));
    }
  }

  @SuppressWarnings("HttpUrlsUsage")
  public String getURI(
      HttpRequest httpRequest,
      Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
    String uri = "";
    if (httpRequest.getPath() != null) {
      if (httpRequest.getQueryStringParameters() != null
          && isNotBlank(httpRequest.getQueryStringParameters().getRawParameterString())) {
        uri =
            httpRequest.getPath()
                + "?"
                + httpRequest.getQueryStringParameters().getRawParameterString();
      } else {
        QueryStringEncoder queryStringEncoder = new QueryStringEncoder(httpRequest.getPath());
        for (Parameter parameter : httpRequest.getQueryStringParameterList()) {
          for (String value : parameter.getValues()) {
            queryStringEncoder.addParam(parameter.getName(), value);
          }
        }
        uri = queryStringEncoder.toString();
      }
    }
    if (proxyConfigurations != null
        && proxyConfigurations.get(ProxyConfiguration.Type.HTTP) != null
        && !Boolean.TRUE.equals(httpRequest.isSecure())) {
      if (isNotBlank(httpRequest.getFirstHeader(HOST.toString()))) {
        uri = "http://" + httpRequest.getFirstHeader(HOST.toString()) + uri;
      } else if (httpRequest.getRemoteAddress() != null) {
        uri = "http://" + httpRequest.getRemoteAddress() + uri;
      }
    }
    return uri;
  }

  private ByteBuf getBody(HttpRequest httpRequest) {
    return bodyDecoderEncoder.bodyToByteBuf(httpRequest.getBody());
  }

  private void setHeader(HttpRequest httpRequest, FullHttpRequest request) {
    for (Header header : httpRequest.getHeaderList()) {
      String headerName = header.getName();
      // do not set hop-by-hop headers
      if (!headerName.equalsIgnoreCase(CONTENT_LENGTH.toString())
          && !headerName.equalsIgnoreCase(TRANSFER_ENCODING.toString())
          && !headerName.equalsIgnoreCase(HOST.toString())
          && !headerName.equalsIgnoreCase(ACCEPT_ENCODING.toString())) {
        if (!header.getValues().isEmpty()) {
          for (String headerValue : header.getValues()) {
            request.headers().add(headerName, headerValue);
          }
        } else {
          request.headers().add(headerName, "");
        }
      }
    }

    if (isNotBlank(httpRequest.getFirstHeader(HOST.toString()))) {
      request.headers().add(HOST, httpRequest.getFirstHeader(HOST.toString()));
    }
    request.headers().set(ACCEPT_ENCODING, GZIP + "," + DEFLATE);
    if (HttpProtocol.HTTP_2.equals(httpRequest.getProtocol())) {
      HttpScheme scheme =
          Boolean.TRUE.equals(httpRequest.isSecure()) ? HttpScheme.HTTPS : HttpScheme.HTTP;
      request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
      Integer streamId = httpRequest.getStreamId();
      if (streamId != null) {
        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
      }
    }
    request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
    if (isKeepAlive(request)) {
      request.headers().set(CONNECTION, KEEP_ALIVE);
    } else {
      request.headers().set(CONNECTION, CLOSE);
    }
  }
}
