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
package de.gematik.rbellogger.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.gematik.test.tiger.exceptions.RbelHostnameFormatException;
import java.io.IOException;
import java.io.Serializable;
import java.net.*;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@Slf4j
@JsonSerialize(using = RbelSocketAddress.RbelHostnameSerializer.class)
@JsonDeserialize(using = RbelSocketAddress.RbelHostnameDeserializer.class)
public class RbelSocketAddress implements Serializable {

  private final RbelInternetAddress address;
  private final int port;

  public RbelSocketAddress(RbelInternetAddress address, int port) {
    this.address = address;
    this.port = port;
  }

  public static Optional<RbelSocketAddress> fromString(final String value) {
    try {
      if (StringUtils.isBlank(value)) {
        return Optional.empty();
      }

      if (value.startsWith("[") && value.contains("]")) {
        return parseIpv6WithBracketing(value);
      } else if (value.contains(":")) {
        return parseUnbracketed(value);
      } else {
        // No port specified
        return Optional.ofNullable(
            RbelSocketAddress.builder()
                .address(RbelInternetAddressParser.parseInetAddress(value))
                .build());
      }
    } catch (Exception e) {
      throw new RbelHostnameFormatException("Unable to parse hostname: '" + value + "'", e);
    }
  }

  private static Optional<RbelSocketAddress> parseUnbracketed(String value) {
    // IPv4 or unbracketed IPv6 with port
    final int lastColon = value.lastIndexOf(":");
    String hostname = value.substring(0, lastColon);
    int port = Integer.parseInt(value.substring(lastColon + 1));
    return Optional.ofNullable(
        RbelSocketAddress.builder()
            .address(RbelInternetAddressParser.parseInetAddress(hostname))
            .port(port)
            .build());
  }

  private static Optional<RbelSocketAddress> parseIpv6WithBracketing(String value) {
    // IPv6 address in brackets, possibly with port
    int closingBracket = value.indexOf(']');
    String hostname = value.substring(1, closingBracket);
    int port = 0;
    if (closingBracket + 1 < value.length() && value.charAt(closingBracket + 1) == ':') {
      port = Integer.parseInt(value.substring(closingBracket + 2));
    }
    return Optional.ofNullable(
        RbelSocketAddress.builder()
            .address(RbelInternetAddressParser.parseInetAddress(hostname))
            .port(port)
            .build());
  }

  public static RbelSocketAddress create(SocketAddress clientAddress) {
    if (clientAddress == null) {
      return null;
    }
    if (clientAddress instanceof InetSocketAddress inetSocketAddress) {
      if (inetSocketAddress.getAddress() != null) {
        return new RbelSocketAddress(
            RbelInternetAddress.fromInetAddress(inetSocketAddress.getAddress()),
            inetSocketAddress.getPort());
      }
      if (inetSocketAddress.getHostString() != null) {
        val inetAddress =
            RbelInternetAddressParser.parseInetAddress(inetSocketAddress.getHostString());
        return new RbelSocketAddress(inetAddress, inetSocketAddress.getPort());
      }
    }
    throw new RbelHostnameFormatException(
        "Unable to parse socket address: '"
            + clientAddress
            + "' - only InetSocketAddress supported");
  }

  public SocketAddress asSocketAddress() {
    return address
        .toInetAddress()
        .map(adr -> new InetSocketAddress(adr, port))
        .orElseGet(() -> InetSocketAddress.createUnresolved(address.printValidHostname(), port));
  }

  public static Optional<RbelSocketAddress> generateFromUrl(String url) {
    if (StringUtils.isEmpty(url)) {
      return Optional.empty();
    }

    try {
      final URI uri = new URI(url);
      if (StringUtils.isEmpty(uri.getHost())) {
        return Optional.empty();
      }

      if (uri.getPort() > 0) {
        return Optional.of(RbelSocketAddress.create(uri.getHost(), uri.getPort()));
      } else if ("http".equals(uri.getScheme())) {
        return Optional.of(RbelSocketAddress.create(uri.getHost(), 80));
      } else if ("https".equals(uri.getScheme())) {
        return Optional.of(RbelSocketAddress.create(uri.getHost(), 443));
      } else {
        return Optional.of(RbelSocketAddress.create(uri.getHost(), 0));
      }
    } catch (Exception e) {
      log.debug("Error while trying to parse URL '{}'", url, e);
      return Optional.empty();
    }
  }

  public static RbelSocketAddress create(String address, int port) {
    return new RbelSocketAddress(RbelInternetAddressParser.parseInetAddress(address), port);
  }

  @JsonProperty
  public String toString() {
    if (port > 0) {
      return printHostname() + ":" + port;
    } else {
      return printHostname();
    }
  }

  public String printHostname() {
    return address.printValidHostname();
  }

  public static class RbelHostnameSerializer extends JsonSerializer<RbelSocketAddress> {
    @Override
    public void serialize(
        RbelSocketAddress value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.toString());
    }
  }

  public static class RbelHostnameDeserializer extends JsonDeserializer<RbelSocketAddress> {

    @Override
    public RbelSocketAddress deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      return RbelSocketAddress.fromString(p.getValueAsString()).orElse(null);
    }
  }

  public boolean isLoopbackAddress() {
    try {
      return address.toInetAddress().map(InetAddress::isLoopbackAddress).orElse(false);
    } catch (Exception e) {
      return false;
    }
  }
}
