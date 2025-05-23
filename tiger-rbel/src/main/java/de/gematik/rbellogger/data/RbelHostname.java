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
package de.gematik.rbellogger.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.gematik.rbellogger.exceptions.RbelHostnameFormatException;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
@Slf4j
@JsonSerialize(using = RbelHostname.RbelHostnameSerializer.class)
@JsonDeserialize(using = RbelHostname.RbelHostnameDeserializer.class)
public class RbelHostname implements Serializable {

  private final String hostname;
  private final int port;

  public static Optional<RbelHostname> fromString(final String value) {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }

    if (value.contains(":")) {
      String[] hostnameValues = value.split(":");
      int port = Integer.parseInt(hostnameValues[1]);

      try {
        return Optional.ofNullable(
            RbelHostname.builder().hostname(hostnameValues[0]).port(port).build());
      } catch (Exception e) {
        throw new RbelHostnameFormatException("Unable to parse hostname: '" + value + "'", e);
      }
    } else {
      return Optional.ofNullable(RbelHostname.builder().hostname(value).build());
    }
  }

  public static RbelHostname create(SocketAddress clientAddress) {
    if (clientAddress == null) {
      return null;
    }
    if (clientAddress instanceof InetSocketAddress inetSocketAddress) {
      return new RbelHostname(inetSocketAddress.getHostName(), inetSocketAddress.getPort());
    } else {
      return new RbelHostname(clientAddress.toString(), 0);
    }
  }

  public SocketAddress asSocketAddress() {
    return new InetSocketAddress(hostname, port);
  }

  public static Optional<Object> generateFromUrl(String url) {
    if (StringUtils.isEmpty(url)) {
      return Optional.empty();
    }

    try {
      final URI uri = new URI(url);
      if (StringUtils.isEmpty(uri.getHost())) {
        return Optional.empty();
      }

      if (uri.getPort() > 0) {
        return Optional.of(new RbelHostname(uri.getHost(), uri.getPort()));
      } else if ("http".equals(uri.getScheme())) {
        return Optional.of(new RbelHostname(uri.getHost(), 80));
      } else if ("https".equals(uri.getScheme())) {
        return Optional.of(new RbelHostname(uri.getHost(), 443));
      } else {
        return Optional.of(new RbelHostname(uri.getHost(), 0));
      }
    } catch (Exception e) {
      log.debug("Error while trying to parse URL '{}'", url, e);
      return Optional.empty();
    }
  }

  @JsonProperty
  public String toString() {
    if (port > 0) {
      return hostname + ":" + port;
    } else {
      return hostname;
    }
  }

  public boolean isLocalHost() {
    return "localhost".equals(hostname) || "127.0.0.1".equals(hostname);
  }

  public static class RbelHostnameSerializer extends JsonSerializer<RbelHostname> {
    @Override
    public void serialize(RbelHostname value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.toString());
    }
  }

  public static class RbelHostnameDeserializer extends JsonDeserializer<RbelHostname> {

    @Override
    public RbelHostname deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return RbelHostname.fromString(p.getValueAsString()).orElse(null);
    }
  }
}
