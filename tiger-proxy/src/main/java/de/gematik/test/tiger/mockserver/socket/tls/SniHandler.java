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
package de.gematik.test.tiger.mockserver.socket.tls;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.HttpProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import java.security.cert.Certificate;
import java.util.Optional;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/*
 * @author jamesdbloom
 */
@Slf4j
public class SniHandler extends AbstractSniHandler<SslContext> {

  public static final AttributeKey<SSLEngine> UPSTREAM_SSL_ENGINE =
      AttributeKey.valueOf("UPSTREAM_SSL_ENGINE");
  public static final AttributeKey<SslHandler> UPSTREAM_SSL_HANDLER =
      AttributeKey.valueOf("UPSTREAM_SSL_HANDLER");
  public static final AttributeKey<Certificate[]> UPSTREAM_CLIENT_CERTIFICATES =
      AttributeKey.valueOf("UPSTREAM_CLIENT_CERTIFICATES");
  public static final AttributeKey<SSLSession> SSL_SESSION = AttributeKey.valueOf("SSL_SESSION");
  public static final AttributeKey<HttpProtocol> NEGOTIATED_APPLICATION_PROTOCOL =
      AttributeKey.valueOf("NEGOTIATED_APPLICATION_PROTOCOL");
  public static final AttributeKey<TigerPkiIdentity> SERVER_IDENTITY =
      AttributeKey.valueOf("SERVER_IDENTITY");

  private final MockServerConfiguration configuration;
  private final NettySslContextFactory nettySslContextFactory;

  public SniHandler(
      MockServerConfiguration configuration, NettySslContextFactory nettySslContextFactory) {
    this.configuration = configuration;
    this.nettySslContextFactory = nettySslContextFactory;
  }

  @Override
  protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) {
    if (isNotBlank(hostname)) {
      configuration.addSubjectAlternativeName(hostname);
    }
    val serverContextAndIdentity = nettySslContextFactory.createServerSslContext(hostname);
    ctx.channel().attr(SERVER_IDENTITY).set(serverContextAndIdentity.getValue());
    return ctx.executor().newSucceededFuture(serverContextAndIdentity.getKey());
  }

  @Override
  protected void onLookupComplete(
      ChannelHandlerContext ctx, String hostname, Future<SslContext> sslContextFuture) {
    if (!sslContextFuture.isSuccess()) {
      final Throwable cause = sslContextFuture.cause();
      if (cause instanceof Error error) {
        throw error;
      }
      throw new DecoderException("Failed to get the SslContext for " + hostname, cause);
    } else {
      try {
        replaceHandler(ctx, sslContextFuture);
      } catch (RuntimeException cause) {
        PlatformDependent.throwException(cause);
      }
    }
  }

  private void replaceHandler(ChannelHandlerContext ctx, Future<SslContext> sslContextFuture) {
    SslHandler sslHandler = null;
    try {
      final SslContext sslContext = sslContextFuture.getNow();
      sslHandler = sslContext.newHandler(ctx.alloc());
      if (sslHandler.engine() instanceof OpenSslEngine openSslEngine
          && configuration.ocspResponseSupplier() != null) {
        try {
          val serverIdentity = ctx.channel().attr(SERVER_IDENTITY).get();
          openSslEngine.setOcspResponse(
              configuration.ocspResponseSupplier().apply(serverIdentity.getCertificate()));
        } catch (Exception e) {
          log.warn("Failed to set OCSP response", e);
        }
      }

      ctx.channel().attr(UPSTREAM_SSL_ENGINE).set(sslHandler.engine());
      ctx.channel().attr(UPSTREAM_SSL_HANDLER).set(sslHandler);
      ctx.pipeline().replace(this, "SslHandler#0", sslHandler);
      sslHandler = null;
    } finally {
      // Since the SslHandler was not inserted into the pipeline the ownership of the SSLEngine was
      // not transferred to the SslHandler.
      // See https://github.com/netty/netty/issues/5678
      if (sslHandler != null) {
        ReferenceCountUtil.safeRelease(sslHandler.engine());
      }
    }
  }

  public static Certificate[] retrieveClientCertificates(ChannelHandlerContext ctx) {
    Certificate[] clientCertificates = null;
    if (ctx.channel().attr(UPSTREAM_CLIENT_CERTIFICATES).get() != null) {
      clientCertificates = ctx.channel().attr(UPSTREAM_CLIENT_CERTIFICATES).get();
    } else if (ctx.channel().attr(UPSTREAM_SSL_ENGINE).get() != null) {
      SSLEngine sslEngine = ctx.channel().attr(UPSTREAM_SSL_ENGINE).get();
      if (sslEngine != null) {
        SSLSession sslSession = sslEngine.getSession();
        if (sslSession != null) {
          try {
            ctx.channel().attr(SSL_SESSION).set(sslSession);
            Certificate[] peerCertificates = sslSession.getPeerCertificates();
            ctx.channel().attr(UPSTREAM_CLIENT_CERTIFICATES).set(peerCertificates);
            return peerCertificates;
          } catch (SSLPeerUnverifiedException ignore) {
            log.trace("no client certificate chain as client did not complete mTLS");
          }
        }
      }
    }
    return clientCertificates;
  }

  public static Optional<HttpProtocol> getAlpnProtocol(ChannelHandlerContext ctx) {
    try {
      if (ctx == null || ctx.channel() == null) {
        return Optional.empty();
      }
      if (ctx.channel().attr(NEGOTIATED_APPLICATION_PROTOCOL).get() != null) {
        return Optional.ofNullable(ctx.channel().attr(NEGOTIATED_APPLICATION_PROTOCOL).get());
      }
      if (ctx.channel().attr(UPSTREAM_SSL_HANDLER).get() == null) {
        return Optional.empty();
      }
      HttpProtocol protocol = null;
      SslHandler sslHandler = ctx.channel().attr(UPSTREAM_SSL_HANDLER).get();
      String negotiatedApplicationProtocol = sslHandler.applicationProtocol();
      if (isNotBlank(negotiatedApplicationProtocol)) {
        if (negotiatedApplicationProtocol.equalsIgnoreCase(ApplicationProtocolNames.HTTP_2)) {
          protocol = HttpProtocol.HTTP_2;
        } else if (negotiatedApplicationProtocol.equalsIgnoreCase(
            ApplicationProtocolNames.HTTP_1_1)) {
          protocol = HttpProtocol.HTTP_1_1;
        }
        ctx.channel().attr(NEGOTIATED_APPLICATION_PROTOCOL).set(protocol);
        log.trace("found ALPN protocol:{}", negotiatedApplicationProtocol);
      }
      return Optional.ofNullable(protocol);
    } catch (RuntimeException throwable) {
      log.warn("exception reading ALPN protocol", throwable);
      return Optional.empty();
    }
  }
}
