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
package de.gematik.test.tiger.util;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import de.gematik.rbellogger.util.RbelInternetAddressParser;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.util.PatternMatchUtils;

public class NoProxyUtils {

  /**
   * Whether traffic to the target has to go through the forward proxy. The target is compared by
   * its name and, if known, by its resolved address. No reverse lookup is done.
   */
  public static boolean shouldUseProxyForHost(
      String targetHost, InetAddress resolvedAddress, List<String> noProxyHosts) {
    return findBypassingNoProxyEntry(targetHost, resolvedAddress, noProxyHosts).isEmpty();
  }

  /**
   * The same decision for a socket address. If the address is not resolved yet, the host name is
   * resolved forward, so that CIDR and IP entries can still match.
   */
  public static boolean shouldUseProxyForAddress(
      InetSocketAddress address, List<String> noProxyHosts) {
    if (address == null || noProxyHosts == null || noProxyHosts.isEmpty()) {
      return true;
    }
    final String host = address.getHostString();
    final InetAddress resolved =
        address.getAddress() != null ? address.getAddress() : resolveIfNeeded(host, noProxyHosts);
    return shouldUseProxyForHost(host, resolved, noProxyHosts);
  }

  /**
   * The address of the host, looked up only if a CIDR or IP entry needs it. Name entries never need
   * a lookup, so for a list of names this returns null without touching DNS.
   */
  public static InetAddress resolveIfNeeded(String host, List<String> noProxyHosts) {
    if (host == null || noProxyHosts == null || !needsResolvedAddress(noProxyHosts)) {
      return null;
    }
    return RbelInternetAddressParser.parseInetAddress(host).toInetAddress().orElse(null);
  }

  /** The first no-proxy entry that lets the target bypass the proxy, if there is one. */
  public static Optional<String> findBypassingNoProxyEntry(
      String targetHost, InetAddress resolvedAddress, List<String> noProxyHosts) {
    if (noProxyHosts == null) {
      return Optional.empty();
    }
    return noProxyHosts.stream()
        .filter(entry -> entry != null && !entry.isBlank())
        .filter(entry -> matches(entry.trim(), targetHost, resolvedAddress))
        .findFirst();
  }

  /** Whether the entry is in one of the supported formats: host, .domain, *.domain, CIDR, or *. */
  public static boolean isReadableNoProxyEntry(String entry) {
    if (entry == null || entry.isBlank()) {
      return false;
    }
    final String trimmed = entry.trim();
    if ("*".equals(trimmed) || InetAddresses.isInetAddress(trimmed)) {
      return true;
    }
    if (trimmed.contains("/")) {
      return parseCidr(trimmed).isPresent();
    }
    return isGlob(trimmed) || InternetDomainName.isValid(withoutDomainPrefix(trimmed));
  }

  /** Only CIDR and IP entries compare addresses; name entries never need a lookup. */
  private static boolean needsResolvedAddress(List<String> noProxyHosts) {
    return noProxyHosts.stream()
        .filter(entry -> entry != null && !entry.isBlank())
        .map(String::trim)
        .anyMatch(
            entry ->
                entry.contains("/")
                    || InetAddresses.isInetAddress(entry)
                    || numericGlobAsCidr(entry).isPresent());
  }

  /**
   * "10.*" or "192.168.*" as the address range it describes, so that a target named by host name
   * matches too once it resolves into that range.
   */
  private static Optional<Cidr> numericGlobAsCidr(String entry) {
    if (!entry.endsWith(".*")) {
      return Optional.empty();
    }
    final String[] octets = entry.substring(0, entry.length() - 2).split("\\.", -1);
    if (octets.length > 3) {
      return Optional.empty();
    }
    final String[] network = {"0", "0", "0", "0"};
    for (int i = 0; i < octets.length; i++) {
      if (octets[i].isEmpty()
          || octets[i].length() > 3
          || !octets[i].chars().allMatch(Character::isDigit)
          || Integer.parseInt(octets[i]) > 255) {
        return Optional.empty();
      }
      network[i] = octets[i];
    }
    return Optional.of(
        new Cidr(InetAddresses.forString(String.join(".", network)), 8 * octets.length));
  }

  private static boolean matches(String entry, String targetHost, InetAddress resolvedAddress) {
    if ("*".equals(entry)) {
      return true;
    }
    if (entry.contains("/")) {
      return parseCidr(entry).filter(cidr -> cidr.contains(resolvedAddress)).isPresent();
    }
    if (InetAddresses.isInetAddress(entry)) {
      return resolvedAddress != null && InetAddresses.forString(entry).equals(resolvedAddress);
    }
    if (entry.startsWith(".") || entry.startsWith("*.")) {
      final String domain = withoutDomainPrefix(entry).toLowerCase(Locale.ROOT);
      final String name = targetHost == null ? "" : targetHost.toLowerCase(Locale.ROOT);
      return name.equals(domain) || name.endsWith("." + domain);
    }
    if (isGlob(entry)) {
      return (targetHost != null
              && PatternMatchUtils.simpleMatch(
                  entry.toLowerCase(Locale.ROOT), targetHost.toLowerCase(Locale.ROOT)))
          || numericGlobAsCidr(entry).filter(cidr -> cidr.contains(resolvedAddress)).isPresent();
    }
    final String name = targetHost == null ? "" : targetHost.toLowerCase(Locale.ROOT);
    final String host = entry.toLowerCase(Locale.ROOT);
    return name.equals(host) || name.endsWith("." + host);
  }

  /** A host pattern with "*" wildcards anywhere ("10.*", "*corp.de", "a.*.b"). */
  private static boolean isGlob(String entry) {
    return entry.indexOf('*') >= 0
        && entry
            .chars()
            .allMatch(
                c -> c == '*' || c == '.' || c == '-' || c == '_' || Character.isLetterOrDigit(c));
  }

  private static String withoutDomainPrefix(String entry) {
    if (entry.startsWith("*.")) {
      return entry.substring(2);
    }
    if (entry.startsWith(".")) {
      return entry.substring(1);
    }
    return entry;
  }

  private static Optional<Cidr> parseCidr(String entry) {
    final String[] parts = entry.split("/", -1);
    if (parts.length != 2 || !InetAddresses.isInetAddress(parts[0])) {
      return Optional.empty();
    }
    final InetAddress network = InetAddresses.forString(parts[0]);
    final int maxPrefix = network.getAddress().length * 8;
    try {
      final int prefix = Integer.parseInt(parts[1]);
      if (prefix < 0 || prefix > maxPrefix) {
        return Optional.empty();
      }
      return Optional.of(new Cidr(network, prefix));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private record Cidr(InetAddress network, int prefix) {

    boolean contains(InetAddress address) {
      if (address == null || address.getAddress().length != network.getAddress().length) {
        return false;
      }
      final byte[] net = network.getAddress();
      final byte[] candidate = address.getAddress();
      for (int bit = 0; bit < prefix; bit++) {
        final int index = bit / 8;
        final int mask = 0x80 >> (bit % 8);
        if ((net[index] & mask) != (candidate[index] & mask)) {
          return false;
        }
      }
      return true;
    }
  }
}
