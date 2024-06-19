/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMultiMap;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
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

  private class RbelHostnameStructureException extends RuntimeException {
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
                    .filter(StringUtils::isNotEmpty)
                    .filter(s -> !s.startsWith("localhost") && !s.startsWith("127.0.0.1")));
  }
}
