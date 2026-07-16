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
package de.gematik.rbellogger.data.core;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.util.RbelInternetAddressParser;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Slf4j
public class RbelSocketAddressFacet implements RbelFacet {

  private final RbelElement port;
  private final RbelElement domain;
  @Builder.Default private Optional<RbelElement> bundledServerName = Optional.empty();

  public static RbelElement buildRbelSocketAddressFacet(
      RbelElement parentNode, RbelSocketAddress rbelSocketAddress) {
    return buildRbelSocketAddressFacet(parentNode, rbelSocketAddress, null);
  }

  public static RbelElement buildRbelSocketAddressFacet(
      RbelElement parentNode, RbelSocketAddress rbelSocketAddress, String bundledServerName) {
    if (rbelSocketAddress == null) {
      return new RbelElement(null, parentNode);
    }
    final RbelElement result =
        new RbelElement(rbelSocketAddress.toString().getBytes(StandardCharsets.UTF_8), parentNode);
    result.addFacet(
        RbelSocketAddressFacet.builder()
            .port(RbelElement.wrap(result, rbelSocketAddress.getPort()))
            .domain(RbelElement.wrap(result, rbelSocketAddress.printHostname()))
            .bundledServerName(
                Optional.ofNullable(bundledServerName)
                    .map(bundledName -> RbelElement.wrap(result, bundledName)))
            .build());
    return result;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .withSkipIfNull("bundledServerName", bundledServerName.orElse(null))
        .with("port", port)
        .with("domain", domain);
  }

  public String toString() {
    return toRbelSocketAddress().toString();
  }

  public RbelSocketAddress toRbelSocketAddress() {
    return toUnbundledRbelSocketAddress();
  }

  public RbelSocketAddress toUnbundledRbelSocketAddress() {
    val localAddress = RbelInternetAddressParser.parseInetAddress(domain.getRawStringContent());
    return RbelSocketAddress.builder()
        .address(localAddress)
        .port(port.seekValue(Integer.class).orElse(0))
        .build();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(port)
        .append(domain)
        .append(bundledServerName)
        .toHashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    return equals((RbelSocketAddressFacet) obj);
  }

  public boolean equals(RbelSocketAddressFacet other) {
    return domainAndPortEquals(other);
  }

  public boolean domainAndPortEquals(RbelSocketAddressFacet other) {
    return Objects.equals(this.getPort().seekValue(), other.getPort().seekValue())
        && domainMatches(this.getDomain().printValue(), other.getDomain().printValue());
  }

  private boolean domainMatches(Optional<String> thisDomain, Optional<String> otherDomain) {
    return thisDomain
        .map(Object::toString)
        .map(RbelSocketAddressFacet::canonicalize)
        .map(
            thisHost ->
                otherDomain
                    .map(Object::toString)
                    .map(RbelSocketAddressFacet::canonicalize)
                    .map(thisHost::equals)
                    .orElse(false))
        .orElse(false);
  }

  @SneakyThrows
  private static String canonicalize(String hostname) {
    try {
      return InetAddress.getByName(hostname).getCanonicalHostName();
    } catch (UnknownHostException e) {
      return hostname;
    }
  }
}
