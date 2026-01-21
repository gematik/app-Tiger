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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelInternetAddressParser {

  private static final Map<String, RbelInternetAddress> CACHE = new ConcurrentHashMap<>();

  private static String cachedLoopbackHostname;

  private static String getLoopbackHostname(InetAddress address) {
    if (cachedLoopbackHostname == null) {
      cachedLoopbackHostname = address.getHostName();
    }
    return cachedLoopbackHostname;
  }

  public static RbelInternetAddress parseInetAddress(String addressString) {
    if (addressString == null || addressString.trim().isEmpty()) {
      throw new IllegalArgumentException("Address string cannot be null or empty.");
    }

    return CACHE.computeIfAbsent(
        addressString, RbelInternetAddressParser::parseInetAddressUncached);
  }

  private static RbelInternetAddress parseInetAddressUncached(String addressString) {
    int slashIndex = addressString.indexOf('/');

    if (slashIndex != -1) {
      return parseJavaHostnameAddressScheme(addressString, slashIndex);
    } else {
      return parseRegularHostname(addressString);
    }
  }

  private static boolean isLikelyIpAddress(String input) {
    if (input == null || input.isEmpty()) return false;
    char first = input.charAt(0);
    return Character.isDigit(first) || input.contains(":");
  }

  private static RbelInternetAddress parseRegularHostname(String addressString) {
    try {
      InetAddress inetAddress = InetAddress.getByName(addressString);
      byte[] ipBytes = inetAddress.getAddress();
      String hostname = computeHostname(addressString, inetAddress);
      return new RbelInternetAddress(hostname, ipBytes);
    } catch (UnknownHostException e) {
      return new RbelInternetAddress(addressString, null);
    }
  }

  private static String computeHostname(String addressString, InetAddress inetAddress) {
    String hostname;
    if (isLikelyIpAddress(addressString)) {
      if (inetAddress.isLoopbackAddress()) {
        hostname = getLoopbackHostname(inetAddress);
      } else {
        hostname = null;
      }
    } else {
      hostname = inetAddress.getHostName();
    }
    return hostname;
  }

  /**
   * Parses an address string in the Java hostname/address scheme (e.g., "hostname/ip-address").
   *
   * @param addressString
   * @param slashIndex
   * @return
   */
  private static RbelInternetAddress parseJavaHostnameAddressScheme(
      String addressString, int slashIndex) {
    String hostname = addressString.substring(0, slashIndex);
    String ipAddress = addressString.substring(slashIndex + 1);
    try {
      byte[] ipBytes = InetAddress.getByName(ipAddress).getAddress();
      return new RbelInternetAddress(StringUtils.isEmpty(hostname) ? ipAddress : hostname, ipBytes);
    } catch (UnknownHostException e) {
      return new RbelInternetAddress(hostname, null);
    }
  }
}
