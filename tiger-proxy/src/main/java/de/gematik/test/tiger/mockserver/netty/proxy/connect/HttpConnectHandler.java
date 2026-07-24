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
package de.gematik.test.tiger.mockserver.netty.proxy.connect;

import static de.gematik.test.tiger.mockserver.model.HttpProtocol.HTTP_1_1;
import static de.gematik.test.tiger.mockserver.model.HttpProtocol.HTTP_2;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.response;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.disableSslUpstreamAndDownstream;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledDownstream;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.PREFERRED_UPSTREAM_KEY_ALGORITHM;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.SERVER_IDENTITY;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.getAlpnProtocol;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.codec.Http2PipelineHelper;
import de.gematik.test.tiger.mockserver.codec.MockServerHttpServerCodec;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.HttpProtocol;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.netty.MockServer;
import de.gematik.test.tiger.mockserver.netty.proxy.relay.DownstreamProxyRelayHandler;
import de.gematik.test.tiger.mockserver.netty.proxy.relay.RelayConnectHandler;
import de.gematik.test.tiger.mockserver.netty.proxy.relay.UpstreamProxyRelayHandler;
import de.gematik.test.tiger.mockserver.netty.unification.HttpContentLengthRemover;
import de.gematik.test.tiger.mockserver.netty.unification.MessagePostProcessorAdapter;
import de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

/**
 * HTTP-specific implementation of RelayConnectHandler. Handles HTTP CONNECT requests and sets up
 * HTTP/HTTPS relay pipelines.
 *
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@Slf4j
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

  public HttpConnectHandler(
      MockServerConfiguration configuration, MockServer server, String host, int port) {
    super(configuration, server, host, port);
  }

  @Override
  protected void removeCodecSupport(ChannelHandlerContext ctx) {
    ChannelPipeline pipeline = ctx.pipeline();
    removeHandler(pipeline, HttpServerCodec.class);
    removeHandler(pipeline, HttpContentDecompressor.class);
    removeHandler(pipeline, HttpObjectAggregator.class);
    removeHandler(pipeline, MockServerHttpServerCodec.class);
    if (pipeline.get(this.getClass()) != null) {
      pipeline.remove(this);
    }
  }

  @Override
  protected Object successResponse(Object request) {
    return response();
  }

  @Override
  protected Object failureResponse(Object request) {
    return response().withStatusCode(HttpResponseStatus.BAD_GATEWAY.code());
  }

  @Override
  protected void configureRelayPipelines(
      ChannelHandlerContext proxyClientCtx,
      ChannelHandlerContext forwardProxyCtx,
      HttpRequest request,
      ChannelFuture clientFuture) {
    removeCodecSupport(proxyClientCtx);
    HttpProtocol httpProtocol = getAlpnProtocol(proxyClientCtx).orElse(HTTP_1_1);

    configureForwardProxyPipeline(proxyClientCtx, forwardProxyCtx, request, httpProtocol);
    configureClientPipeline(proxyClientCtx, forwardProxyCtx, clientFuture, httpProtocol);
  }

  private void configureForwardProxyPipeline(
      ChannelHandlerContext proxyClientCtx,
      ChannelHandlerContext forwardProxyCtx,
      HttpRequest request,
      HttpProtocol httpProtocol) {
    ChannelPipeline pipeline = forwardProxyCtx.channel().pipeline();

    if (isSslEnabledDownstream(proxyClientCtx.channel())) {
      log.trace("Adding SSL Handler to forward proxy pipeline for {}:{}", host, port);
      pipeline.addLast(
          getServer()
              .getClientSslContextFactory()
              .createClientSslContext(
                  httpProtocol, request.socketAddressFromHostHeader().getHostName())
              .newHandler(forwardProxyCtx.alloc(), host, port));
    }

    if (httpProtocol == HTTP_2) {
      addHttp2ClientCodecs(pipeline);
    } else {
      pipeline.addLast(
          new HttpClientCodec(
              getConfiguration().maxInitialLineLength(),
              getConfiguration().maxHeaderSize(),
              getConfiguration().maxChunkSize()));
      pipeline.addLast(new HttpContentDecompressor());
      // TODO: make max content length configurable instead of Integer.MAX_VALUE
      pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
    }
    pipeline.addLast(new DownstreamProxyRelayHandler(proxyClientCtx.channel()));
  }

  private void configureClientPipeline(
      ChannelHandlerContext proxyClientCtx,
      ChannelHandlerContext forwardProxyCtx,
      ChannelFuture clientFuture,
      HttpProtocol httpProtocol) {
    ChannelPipeline pipeline = proxyClientCtx.channel().pipeline();

    addSslHandlerIfNeeded(proxyClientCtx, clientFuture, pipeline);
    addHttpCodecs(pipeline, httpProtocol);
    pipeline.addLast(
        new UpstreamProxyRelayHandler(
            getServer(), proxyClientCtx.channel(), forwardProxyCtx.channel()));
  }

  private void addSslHandlerIfNeeded(
      ChannelHandlerContext proxyClientCtx, ChannelFuture clientFuture, ChannelPipeline pipeline) {
    if (isSslEnabledUpstream(proxyClientCtx.channel()) && pipeline.get(SslHandler.class) == null) {
      Pair<SslContext, TigerPkiIdentity> serverSslContext =
          getServer()
              .getServerSslContextFactory()
              .createServerSslContext(
                  host, proxyClientCtx.channel().attr(PREFERRED_UPSTREAM_KEY_ALGORITHM).get());
      clientFuture.channel().attr(SERVER_IDENTITY).set(serverSslContext.getValue());
      pipeline.addLast(serverSslContext.getKey().newHandler(proxyClientCtx.alloc()));
    }
  }

  private void addHttpCodecs(ChannelPipeline pipeline, HttpProtocol httpProtocol) {
    if (httpProtocol == HTTP_2) {
      addHttp2Codecs(pipeline);
    } else {
      addHttp1Codecs(pipeline);
    }
  }

  private void addHttp2Codecs(ChannelPipeline pipeline) {
    Http2PipelineHelper.addHttp2Codecs(
        pipeline,
        true,
        false,
        HttpConnectHandler.class,
        getConfiguration().http2FrameParsingActive()
            ? getConfiguration().binaryProxyListener()
            : null);
  }

  private void addHttp2ClientCodecs(ChannelPipeline pipeline) {
    Http2PipelineHelper.addHttp2Codecs(
        pipeline,
        false,
        true,
        HttpConnectHandler.class,
        getConfiguration().http2FrameParsingActive()
            ? getConfiguration().binaryProxyListener()
            : null);
  }

  private void addHttp1Codecs(ChannelPipeline pipeline) {
    pipeline.addLast(
        new HttpServerCodec(
            getConfiguration().maxInitialLineLength(),
            getConfiguration().maxHeaderSize(),
            getConfiguration().maxChunkSize()));
    pipeline.addLast(new HttpContentDecompressor());
    // TODO: make max content length configurable instead of Integer.MAX_VALUE
    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
  }

  @Override
  protected void prepareForSubsequentTraffic(ChannelHandlerContext ctx) {
    ChannelPipeline pipeline = ctx.pipeline();

    removeHandler(pipeline, HttpServerCodec.class);
    removeHandler(pipeline, HttpContentDecompressor.class);
    removeHandler(pipeline, HttpObjectAggregator.class);
    removeHandler(pipeline, MockServerHttpServerCodec.class);
    removeHandler(pipeline, HttpContentLengthRemover.class);
    removeHandler(pipeline, MessagePostProcessorAdapter.class);
    removeHandler(pipeline, HttpConnectHandler.class);

    // A local CONNECT tunnel may carry either plain HTTP/WebSocket or TLS. Reset the optimistic
    // CONNECT defaults so protocol detection is driven by the first bytes after 200 Established.
    disableSslUpstreamAndDownstream(ctx.channel());

    pipeline.addLast(
        new PortUnificationHandler(
            getConfiguration(),
            getServer(),
            getServer().getHttpState(),
            getServer().getActionHandler(),
            getServer().getInfiniteLoopChecker()));

    log.trace("Pipeline prepared for subsequent HTTP traffic with PortUnificationHandler");
  }
}
