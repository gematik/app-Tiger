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
package de.gematik.test.tiger.mockserver.netty.unification;

import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.INCOMING_CHANNEL;
import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.OUTGOING_CHANNEL;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.HTTP_CLIENT;

import de.gematik.test.tiger.mockserver.codec.MockServerBinaryClientCodec;
import de.gematik.test.tiger.mockserver.codec.MockServerHttpClientCodec;
import de.gematik.test.tiger.mockserver.codec.MockServerHttpServerCodec;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler;
import de.gematik.test.tiger.mockserver.httpclient.HttpClientHandler;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import de.gematik.test.tiger.mockserver.netty.HttpRequestHandler;
import de.gematik.test.tiger.mockserver.netty.proxy.BinaryHandler;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyException;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * This handler upgrades the two pipelines (incoming and outgoing) to binary mode, when a websocket
 * handshake message is received. This handler is supposed to be part of the outgoing pipeline:
 *
 * <p>Client <-incoming-> Proxy <-outgoing-> Server
 *
 * <p>The timeline of the upgrade:
 *
 * <pre> Incoming-Pipeline                 Outgoing-Pipeline
 *
 * GET Connection: Upgrade ->
 *                                       GET Connection: Upgrade ->
 *                                       HTTP 101 <-
 *                                              - queue incoming upgrade
 *                                              - queue outgoing upgrade
 *                                              - switch to buffer mode
 *
 *                                       After pipeline passed:
 *                                              - switch outgoing to binary
 *
 * HTTP 101 <- MessagePostProcessorAdapter triggers:
 *       - call runnable from WebSocketUpgradeHandler:
 *                                              - switch incoming to binary
 *                                              - clear outgoing-pipeline buffer
 *                                              - remove self from pipeline
 *        - clear post-listener
 *
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketUpgradeHandler extends ChannelInboundHandlerAdapter {
  private static final Set<Class<? extends ChannelHandler>> httpHandlerClasses =
      Set.of(
          HttpClientCodec.class,
          HttpClientHandler.class,
          HttpContentDecompressor.class,
          HttpContentLengthRemover.class,
          HttpObjectAggregator.class,
          HttpRequestHandler.class,
          HttpServerCodec.class,
          MockServerHttpClientCodec.class,
          MockServerHttpServerCodec.class);
  private final MockServerConfiguration configuration;
  private boolean isUpgrading = false;
  private final List<Object> messageQueue = Collections.synchronizedList(new ArrayList<>());

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    synchronized (messageQueue) {
      if (isUpgrading) {
        messageQueue.add(msg);
        return;
      }
    }

    if (msg instanceof HttpResponse httpResponse && httpResponse.isWebsocketHandshake()) {
      upgradeConnectionToWebSocket(ctx, httpResponse);
    }
    ctx.fireChannelRead(msg);
  }

  @SneakyThrows
  private void upgradeConnectionToWebSocket(
      ChannelHandlerContext outgoingContext, HttpResponse msg) {
    this.isUpgrading = true;
    val outgoingPipeline = outgoingContext.pipeline();
    log.atTrace().log("Upgrade connection to WebSocket");

    val incomingChannel =
        Optional.ofNullable(outgoingContext.channel().attr(INCOMING_CHANNEL).get()).orElseThrow();
    incomingChannel.attr(OUTGOING_CHANNEL).set(outgoingContext.channel());
    outgoingContext.channel().attr(INCOMING_CHANNEL).set(incomingChannel);
    outgoingContext.channel().eventLoop().execute(() -> upgradeOutgoingPipeline(outgoingPipeline));

    incomingChannel
        .attr(MessagePostProcessorAdapter.POSTPROCESSOR_KEY)
        .set(
            () -> {
              upgradeIncomingPipeline(incomingChannel.pipeline());
              clearBuffer(outgoingContext);
              outgoingPipeline.remove(this);
            });
  }

  private void upgradeOutgoingPipeline(ChannelPipeline outgoingPipeline) {
    removeHttpHandlers(outgoingPipeline);
    outgoingPipeline.addLast(new MockServerBinaryClientCodec());
    outgoingPipeline.addLast(new WebSocketCloseHandler());
    outgoingPipeline.addLast(new BinaryBridgeHandler(configuration));
  }

  public void upgradeIncomingPipeline(ChannelPipeline pipeline) {
    removeHttpHandlers(pipeline);

    val httpClient = pipeline.channel().attr(HTTP_CLIENT).get();
    if (httpClient == null) {
      log.error("No http client found!");
      throw new TigerProxyException("HttpClient is null");
    }
    pipeline.addLast(new BinaryHandler(configuration, httpClient));
  }

  @SneakyThrows
  private void clearBuffer(ChannelHandlerContext ctx) {
    synchronized (messageQueue) {
      for (Object o : messageQueue) {
        ctx.fireChannelRead(o);
      }
      messageQueue.clear();
      isUpgrading = false;
    }
  }

  private static void removeHttpHandlers(ChannelPipeline pipeline) {
    pipeline.channel().attr(ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE).set(false);
    StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(pipeline.iterator(), Spliterator.ORDERED), false)
        .map(Entry::getValue)
        .map(ChannelHandler::getClass)
        .filter(httpHandlerClasses::contains)
        .forEach(pipeline::remove);
  }
}
