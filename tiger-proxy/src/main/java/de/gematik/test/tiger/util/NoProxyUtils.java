/*
 *
 * Copyright 2024 gematik GmbH
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
 */
package de.gematik.test.tiger.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;

public class NoProxyUtils {

  public static boolean shouldUseProxyForHost(
      InetAddress remoteAddress, List<String> noProxyHosts) {
    if (noProxyHosts == null) {
      return true;
    }
    return noProxyHosts.stream()
        .map(String::trim)
        .map(
            host -> {
              try {
                return InetAddress.getByName(host);
              } catch (UnknownHostException e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .noneMatch(a -> remoteAddress.getHostName().equals(a.getHostName()));
  }
}
