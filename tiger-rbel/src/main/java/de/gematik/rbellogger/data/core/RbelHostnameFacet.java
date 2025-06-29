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
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.util.RbelException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Slf4j
public class RbelHostnameFacet implements RbelFacet {

  private final RbelElement port;
  private final RbelElement domain;
  @Builder.Default private Optional<RbelElement> bundledServerName = Optional.empty();

  public static RbelElement buildRbelHostnameFacet(
      RbelElement parentNode, RbelHostname rbelHostname) {
    return buildRbelHostnameFacet(parentNode, rbelHostname, null);
  }

  public static RbelElement buildRbelHostnameFacet(
      RbelElement parentNode, RbelHostname rbelHostname, String bundledServerName) {
    if (rbelHostname == null) {
      return new RbelElement(null, parentNode);
    }
    final RbelElement result =
        new RbelElement(rbelHostname.toString().getBytes(StandardCharsets.UTF_8), parentNode);
    result.addFacet(
        RbelHostnameFacet.builder()
            .port(RbelElement.wrap(result, rbelHostname.getPort()))
            .domain(RbelElement.wrap(result, rbelHostname.getHostname()))
            .bundledServerName(
                Optional.ofNullable(bundledServerName)
                    .map(bundledName -> RbelElement.wrap(parentNode, bundledName)))
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
    return bundledServerName
            .flatMap(el -> el.seekValue(String.class))
            .or(() -> domain.seekValue(String.class))
            .orElseThrow(() -> new RbelHostnameStructureException("Could not find domain-name!"))
        + port.seekValue(Integer.class)
            .map(bundledServerPort -> ":" + bundledServerPort)
            .orElse("");
  }

  public RbelHostname toRbelHostname() {
    return RbelHostname.builder()
        .hostname(
            bundledServerName
                .flatMap(el -> el.seekValue(String.class))
                .or(() -> domain.seekValue(String.class))
                .orElseThrow())
        .port(port.seekValue(Integer.class).orElse(0))
        .build();
  }

  private static class RbelHostnameStructureException extends RbelException { // NOSONAR
    public RbelHostnameStructureException(String s) {
      super(s);
    }
  }

  public static Optional<String> tryToExtractServerName(RbelElement element) {
    final Optional<RbelHostnameFacet> hostnameFacet = element.getFacet(RbelHostnameFacet.class);
    if (hostnameFacet.isEmpty()) {
      return Optional.empty();
    }
    return hostnameFacet
        .flatMap(RbelHostnameFacet::getBundledServerName)
        .filter(e -> e.getRawStringContent() != null)
        .flatMap(e -> Optional.of(e.getRawStringContent()))
        .or(
            () ->
                hostnameFacet
                    .map(RbelHostnameFacet::getDomain)
                    .map(RbelElement::getRawStringContent)
                    .filter(StringUtils::isNotEmpty));
  }

  public boolean domainAndPortEquals(RbelHostnameFacet other) {
    return Objects.equals(this.getPort().seekValue(), other.getPort().seekValue())
        && domainMatches(this.getDomain().printValue(), other.getDomain().printValue());
  }

  private boolean domainMatches(Optional<String> thisDomain, Optional<String> otherDomain) {
    return thisDomain
        .map(Object::toString)
        .map(RbelHostnameFacet::canonicalize)
        .map(
            thisHost ->
                otherDomain
                    .map(Object::toString)
                    .map(RbelHostnameFacet::canonicalize)
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
