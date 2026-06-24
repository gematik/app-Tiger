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

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.WireParseException;

/** Parses DNS queries and generates responses via the resolver chain. */
@Slf4j
@Component
@RequiredArgsConstructor
class DnsMessageProcessor {

  private final ResolverChain resolverChain;

  /** Decodes the wire-format query, runs the resolver chain, and re-encodes the answer. */
  byte[] processQuery(byte[] data) {
    Message query;
    try {
      query = new Message(data);
    } catch (WireParseException e) {
      log.atInfo().addArgument(e::getMessage).log("Malformed DNS query: {}");
      return createFormatErrorResponse(data);
    } catch (IOException e) {
      log.atInfo().addArgument(e::getMessage).log("Failed to parse DNS query: {}");
      return new byte[0];
    }
    Message response;
    try {
      response = resolverChain.resolve(query);
    } catch (RuntimeException e) {
      log.atWarn()
          .addArgument(() -> e.getClass().getSimpleName())
          .addArgument(e::getMessage)
          .log("Resolver chain threw {}: {}");
      response = createServerFailureResponse(query);
    }
    return response.toWire();
  }

  /**
   * Creates a DNS SERVFAIL response (RFC 1035 Response Code 2: Server Failure). Used when the
   * resolver chain encounters an exception.
   */
  private static Message createServerFailureResponse(Message query) {
    Message response = new Message(query.getHeader().getID());
    response.getHeader().setRcode(Rcode.SERVFAIL);
    response.getHeader().setFlag(Flags.QR);
    if (query.getQuestion() != null) {
      response.addRecord(query.getQuestion(), org.xbill.DNS.Section.QUESTION);
    }
    return response;
  }

  /**
   * Creates a DNS FORMERR response (RFC 1035 Response Code 1: Format Error). Used when the query
   * cannot be parsed. This is a best-effort response: it extracts the message ID from the first 2
   * bytes and returns a minimal FORMERR response without question section.
   */
  private static byte[] createFormatErrorResponse(byte[] data) {
    if (data.length < 2) {
      return new byte[0];
    }
    Message response = new Message();
    try {
      response.getHeader().setID(((data[0] & 0xff) << 8) | (data[1] & 0xff));
    } catch (IllegalArgumentException ignored) {
      return new byte[0];
    }
    response.getHeader().setFlag(Flags.QR);
    response.getHeader().setRcode(Rcode.FORMERR);
    return response.toWire();
  }
}
