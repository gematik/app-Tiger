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
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
@AllArgsConstructor
public class RbelInternetAddress {
  String hostname;
  byte[] ipAddress;

  public static RbelInternetAddress fromInetAddress(InetAddress ipAddress) {
    return new RbelInternetAddress(ipAddress.getHostName(), ipAddress.getAddress());
  }

  @SneakyThrows
  public String toString() {
    if (ipAddress != null) {
      if (StringUtils.isBlank(hostname)) {
        return InetAddress.getByAddress(ipAddress).getHostAddress();
      }
      return hostname + "/" + InetAddress.getByAddress(ipAddress).getHostAddress();
    } else {
      return hostname;
    }
  }

  @SneakyThrows
  public String printValidHostname() {
    if (hostname != null) {
      return hostname;
    } else if (ipAddress != null) {
      return InetAddress.getByAddress(ipAddress).getHostAddress();
    } else {
      return "<unknown-host>";
    }
  }

  public Optional<InetAddress> toInetAddress() {
    try {
      if (ipAddress != null) {
        return Optional.of(InetAddress.getByAddress(ipAddress));
      } else {
        return Optional.ofNullable(InetAddress.getByName(hostname));
      }
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
