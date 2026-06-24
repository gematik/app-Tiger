/*
 *
 * Copyright 2021-2026 gematik GmbH
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
 *
 */
package de.gematik.test.tiger.canopy.dns;

import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.config.ResolvConfResolverConfigProvider;

/**
 * Forwarding resolver used by the {@link ResolverChain} for queries that did <em>not</em> hit the
 * proxied-host registry. Wraps {@link ExtendedResolver} configured either from the explicit {@code
 * canopy.upstreamDnsServers} list, or — if empty — from {@code /etc/resolv.conf}.
 *
 * <p>Returns a synthesised SERVFAIL message rather than throwing on upstream failures so that
 * clients receive a normal DNS response.
 */
@Slf4j
@Component
public class SystemDnsResolver {

  private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(5);

  private final Resolver delegate;

  @Autowired
  public SystemDnsResolver(CanopyConfiguration configuration) throws UnknownHostException {
    this.delegate = buildDelegate(configuration);
  }

  /** Test-only constructor allowing a custom delegate (e.g. a Mockito mock). */
  SystemDnsResolver(Resolver delegate) {
    this.delegate = delegate;
  }

  private static Resolver buildDelegate(CanopyConfiguration configuration)
      throws UnknownHostException {
    List<String> upstream = configuration.getUpstreamDnsServers();
    List<Resolver> resolvers = new ArrayList<>();
    if (upstream != null && !upstream.isEmpty()) {
      for (String server : upstream) {
        resolvers.add(new SimpleResolver(server));
      }
      log.info("SystemDnsResolver: using configured upstream servers {}", upstream);
    } else {
      try {
        ResolvConfResolverConfigProvider provider = new ResolvConfResolverConfigProvider();
        provider.initialize();
        for (InetSocketAddress addr : provider.servers()) {
          resolvers.add(new SimpleResolver(addr));
        }
      } catch (RuntimeException e) {
        log.warn(
            "Could not parse /etc/resolv.conf: {}. Falling back to default SimpleResolver.",
            e.getMessage());
      }
      if (resolvers.isEmpty()) {
        // Last-resort default: dnsjava picks the platform default.
        resolvers.add(new SimpleResolver());
      }
      log.info("SystemDnsResolver: using {} system upstream resolver(s)", resolvers.size());
    }
    ExtendedResolver extended = new ExtendedResolver(resolvers.toArray(Resolver[]::new));
    extended.setTimeout(QUERY_TIMEOUT);
    extended.setRetries(2);
    return extended;
  }

  /** Forwards {@code query} upstream; returns a SERVFAIL message on failure. */
  public Message resolve(Message query) {
    try {
      return delegate.send(query);
    } catch (IOException e) {
      log.atDebug()
          .addArgument(query::getQuestion)
          .addArgument(e::getMessage)
          .log("Upstream resolution failed for {}: {}");
      return servfail(query);
    }
  }

  static Message servfail(Message query) {
    Message response = new Message(query.getHeader().getID());
    response.getHeader().setRcode(Rcode.SERVFAIL);
    if (query.getQuestion() != null) {
      response.addRecord(query.getQuestion(), org.xbill.DNS.Section.QUESTION);
    }
    return response;
  }
}
