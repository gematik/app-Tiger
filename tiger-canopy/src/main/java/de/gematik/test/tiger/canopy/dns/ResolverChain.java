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
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

/**
 * Top-level resolver wired into the DNS server. Decides for every incoming {@link Message} whether
 * the query name matches the {@link ProxiedHostRegistry} (in which case a synthetic answer pointing
 * to the Tiger proxy is returned) or should be forwarded via the {@link SystemDnsResolver}.
 */
@Slf4j
@Component
public class ResolverChain {

  private final ProxiedHostRegistry registry;
  private final ProxyAddressProvider proxyAddresses;
  private final SystemDnsResolver systemResolver;
  private final CanopyConfiguration configuration;

  public ResolverChain(
      ProxiedHostRegistry registry,
      ProxyAddressProvider proxyAddresses,
      SystemDnsResolver systemResolver,
      CanopyConfiguration configuration) {
    this.registry = registry;
    this.proxyAddresses = proxyAddresses;
    this.systemResolver = systemResolver;
    this.configuration = configuration;
  }

  /** Resolves a single DNS query message, returning the response message. Never throws. */
  public Message resolve(Message query) {
    Record question = query.getQuestion();
    if (question == null) {
      return formError(query);
    }
    int type = question.getType();

    // Only A / AAAA queries are intercepted; everything else is forwarded.
    if (type != Type.A && type != Type.AAAA) {
      return systemResolver.resolve(query);
    }

    String name = question.getName().toString(true); // omit trailing dot
    return registry
        .lookup(name)
        .map(entry -> synthesize(query, question, type, entry))
        .orElseGet(() -> systemResolver.resolve(query));
  }

  private Message synthesize(Message query, Record question, int type, ProxiedHostEntry entry) {
    Message response = createResponseMessage(query);
    response.addRecord(question, Section.QUESTION);

    List<InetAddress> proxyIps = proxyAddresses.addressesFor(entry);
    long ttl = configuration.getDefaultTtlSeconds();

    addMatchingRecords(response, question, type, proxyIps, ttl);
    if (response.getSection(Section.ANSWER).isEmpty()) {
      addFallbackRecords(response, question, type, proxyIps, ttl, entry);
    }

    return response;
  }

  private Message createResponseMessage(Message query) {
    Message response = new Message(query.getHeader().getID());
    response.getHeader().setFlag(Flags.QR);
    response.getHeader().setFlag(Flags.AA);
    if (query.getHeader().getFlag(Flags.RD)) {
      response.getHeader().setFlag(Flags.RD);
      response.getHeader().setFlag(Flags.RA);
    }
    return response;
  }

  private void addMatchingRecords(
      Message response, Record question, int type, List<InetAddress> proxyIps, long ttl) {
    for (InetAddress addr : proxyIps) {
      if (type == Type.A && addr instanceof Inet4Address v4) {
        response.addRecord(new ARecord(question.getName(), DClass.IN, ttl, v4), Section.ANSWER);
      } else if (type == Type.AAAA && addr instanceof Inet6Address v6) {
        response.addRecord(new AAAARecord(question.getName(), DClass.IN, ttl, v6), Section.ANSWER);
      }
    }
  }

  private void addFallbackRecords(
      Message response,
      Record question,
      int type,
      List<InetAddress> proxyIps,
      long ttl,
      ProxiedHostEntry entry) {
    for (InetAddress addr : proxyIps) {
      if (addr instanceof Inet4Address v4) {
        response.addRecord(new ARecord(question.getName(), DClass.IN, ttl, v4), Section.ANSWER);
      } else if (addr instanceof Inet6Address v6) {
        response.addRecord(new AAAARecord(question.getName(), DClass.IN, ttl, v6), Section.ANSWER);
      }
    }

    if (!response.getSection(Section.ANSWER).isEmpty()) {
      log.atInfo()
          .addArgument(entry::getHost)
          .addArgument(entry::getMatchType)
          .addArgument(() -> Type.string(type))
          .log(
              "Registry hit for {} ({}) but no proxy address of type {} available; "
                  + "returning all available address families as fallback");
    }
  }

  private static Message formError(Message query) {
    Message response = new Message(query.getHeader().getID());
    response.getHeader().setRcode(Rcode.FORMERR);
    return response;
  }
}
