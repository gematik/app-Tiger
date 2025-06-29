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

import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;

import de.gematik.test.tiger.mockserver.codec.BodyDecoderEncoder;
import de.gematik.test.tiger.mockserver.codec.ExpandedParameterDecoder;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.model.HttpProtocol;
import de.gematik.test.tiger.mockserver.url.URLParser;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.HttpConversionUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Slf4j
public class FullHttpRequestToMockServerHttpRequest {

  private final BodyDecoderEncoder bodyDecoderEncoder;
  private final ExpandedParameterDecoder formParameterParser;
  private final boolean isSecure;
  private final Certificate[] clientCertificates;
  private final Integer port;
  private final JDKCertificateToMockServerX509Certificate jdkCertificateToMockServerX509Certificate;

  public FullHttpRequestToMockServerHttpRequest(
      MockServerConfiguration configuration,
      boolean isSecure,
      Certificate[] clientCertificates,
      Integer port) {
    this.bodyDecoderEncoder = new BodyDecoderEncoder();
    this.formParameterParser = new ExpandedParameterDecoder(configuration);
    this.isSecure = isSecure;
    this.clientCertificates = clientCertificates;
    this.port = port;
    this.jdkCertificateToMockServerX509Certificate =
        new JDKCertificateToMockServerX509Certificate();
  }

  public HttpRequest mapFullHttpRequestToMockServerRequest(
      FullHttpRequest fullHttpRequest,
      List<Header> preservedHeaders,
      SocketAddress senderAddress,
      Optional<HttpProtocol> protocol,
      SSLSession sslSession,
      boolean isProxying) {
    HttpRequest httpRequest = new HttpRequest();
    try {
      if (fullHttpRequest != null) {
        if (fullHttpRequest.decoderResult().isFailure()) {
          log.warn(
              "exception decoding request {}",
              fullHttpRequest.decoderResult().cause().getMessage(),
              fullHttpRequest.decoderResult().cause());
        }
        setMethod(httpRequest, fullHttpRequest);
        httpRequest.setKeepAlive(isKeepAlive(fullHttpRequest));
        httpRequest.setSecure(isSecure);
        httpRequest.setProtocol(protocol.orElse(HttpProtocol.HTTP_1_1));

        setPath(httpRequest, fullHttpRequest);
        setQueryString(httpRequest, fullHttpRequest);
        setHeaders(httpRequest, fullHttpRequest, preservedHeaders);
        setBody(httpRequest, fullHttpRequest);
        setSocketAddress(httpRequest, fullHttpRequest, isSecure, port, senderAddress);
        setForwardProxyRequest(httpRequest, fullHttpRequest, isProxying);

        jdkCertificateToMockServerX509Certificate.setClientCertificates(
            httpRequest, clientCertificates);

        tryToSetTlsParameter(httpRequest, sslSession);
      }
    } catch (RuntimeException e) {
      log.error("exception decoding request{}", fullHttpRequest, e);
    }
    return httpRequest;
  }

  private void setForwardProxyRequest(
      HttpRequest httpRequest, FullHttpRequest fullHttpRequest, boolean isProxying) {
    if (isProxying) {
      httpRequest.setForwardProxyRequest(true);
    } else {
      try {
        final String uriHost = new URI(fullHttpRequest.uri()).getHost();
        if (StringUtils.isNotBlank(uriHost) || fullHttpRequest.method() == HttpMethod.CONNECT) {
          httpRequest.setForwardProxyRequest(true);
        }
      } catch (URISyntaxException e) {
        httpRequest.setForwardProxyRequest(false);
      }
    }
  }

  private void tryToSetTlsParameter(HttpRequest httpRequest, SSLSession sslSession) {
    if (sslSession != null) {
      httpRequest.setTlsVersion(sslSession.getProtocol());
      httpRequest.setCipherSuite(sslSession.getCipherSuite());
    }
  }

  private void setSocketAddress(
      HttpRequest httpRequest,
      FullHttpRequest fullHttpRequest,
      boolean isSecure,
      Integer port,
      SocketAddress senderAddress) {
    httpRequest.setReceiverAddress(isSecure, fullHttpRequest.headers().get("host"), port);
    if (senderAddress instanceof InetSocketAddress) {
      httpRequest.setSenderAddress(StringUtils.removeStart(senderAddress.toString(), "/"));
    }
  }

  private void setMethod(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
    httpRequest.setMethod(fullHttpResponse.method().name());
  }

  private void setPath(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
    httpRequest.setPath(URLParser.returnPath(fullHttpRequest.uri()));
  }

  private void setQueryString(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
    if (fullHttpRequest.uri().contains("?")) {
      httpRequest.setQueryStringParameters(
          formParameterParser.retrieveQueryParameters(fullHttpRequest.uri(), true));
    }
  }

  private void setHeaders(
      HttpRequest httpRequest, FullHttpRequest fullHttpResponse, List<Header> preservedHeaders) {
    HttpHeaders httpHeaders = fullHttpResponse.headers();
    if (!httpHeaders.isEmpty()) {
      Headers headers = new Headers();
      for (String headerName : httpHeaders.names()) {
        headers.withEntry(headerName, httpHeaders.getAll(headerName));
      }
      httpRequest.withHeaders(headers);
    }
    if (preservedHeaders != null && !preservedHeaders.isEmpty()) {
      for (Header preservedHeader : preservedHeaders) {
        httpRequest.withHeader(preservedHeader);
      }
    }
    if (HttpProtocol.HTTP_2.equals(httpRequest.getProtocol())) {
      Integer streamId =
          fullHttpResponse
              .headers()
              .getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
      httpRequest.setStreamId(streamId);
    }
  }

  private void setBody(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
    httpRequest.withBody(bodyDecoderEncoder.byteBufToBody(fullHttpRequest.content()));
  }
}
