/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.socket.tls;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.Protocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import java.security.cert.Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import lombok.extern.slf4j.Slf4j;

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
  public static final AttributeKey<Protocol> NEGOTIATED_APPLICATION_PROTOCOL =
      AttributeKey.valueOf("NEGOTIATED_APPLICATION_PROTOCOL");

  private final MockServerConfiguration configuration;
  private final NettySslContextFactory nettySslContextFactory;

  public SniHandler(MockServerConfiguration configuration, NettySslContextFactory nettySslContextFactory) {
    this.configuration = configuration;
    this.nettySslContextFactory = nettySslContextFactory;
  }

  @Override
  protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) {
    if (isNotBlank(hostname)) {
      configuration.addSubjectAlternativeName(hostname);
    }
    return ctx.executor().newSucceededFuture(nettySslContextFactory.createServerSslContext());
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

  private void replaceHandler(ChannelHandlerContext ctx, Future<SslContext> sslContext) {
    SslHandler sslHandler = null;
    try {
      sslHandler = sslContext.getNow().newHandler(ctx.alloc());
      if (sslHandler.engine() instanceof OpenSslEngine openSslEngine
          && configuration.ocspResponseSupplier() != null) {
        try {
          final KeyAndCertificateFactory keyAndCertificateFactory =
              ((NettySslContextFactory)
                      ctx.channel().attr(AttributeKey.valueOf("NETTY_SSL_CONTEXT_FACTORY")).get())
                  .createKeyAndCertificateFactory();
          openSslEngine.setOcspResponse(
              configuration
                  .ocspResponseSupplier()
                  .apply(keyAndCertificateFactory.x509Certificate()));
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

  public static Protocol getALPNProtocol(ChannelHandlerContext ctx) {
    Protocol protocol = null;
    try {
      if (ctx != null && ctx.channel() != null) {
        if (ctx.channel().attr(NEGOTIATED_APPLICATION_PROTOCOL).get() != null) {
          return ctx.channel().attr(NEGOTIATED_APPLICATION_PROTOCOL).get();
        } else if (ctx.channel().attr(UPSTREAM_SSL_HANDLER).get() != null) {
          SslHandler sslHandler = ctx.channel().attr(UPSTREAM_SSL_HANDLER).get();
          String negotiatedApplicationProtocol = sslHandler.applicationProtocol();
          if (isNotBlank(negotiatedApplicationProtocol)) {
            if (negotiatedApplicationProtocol.equalsIgnoreCase(ApplicationProtocolNames.HTTP_2)) {
              protocol = Protocol.HTTP_2;
            } else if (negotiatedApplicationProtocol.equalsIgnoreCase(
                ApplicationProtocolNames.HTTP_1_1)) {
              protocol = Protocol.HTTP_1_1;
            }
            ctx.channel().attr(NEGOTIATED_APPLICATION_PROTOCOL).set(protocol);
            log.trace("found ALPN protocol:{}", negotiatedApplicationProtocol);
          }
        }
      }
    } catch (RuntimeException throwable) {
      log.warn("exception reading ALPN protocol", throwable);
    }
    return protocol;
  }
}
