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
package de.gematik.test.tiger.common.data.config.tigerproxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the configuration for a forward proxy, including its hostname, port, type, and
 * authentication details.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class ForwardProxyInfo {
  private String hostname;
  private Integer port;
  @Builder.Default private TigerProxyType type = TigerProxyType.HTTP;
  private String username;
  private String password;
  private List<String> noProxyHosts;

  public TigerProxyType getProxyProtocol(String proxyProtocol) {
    if (proxyProtocol.equalsIgnoreCase("http")) {
      return TigerProxyType.HTTP;
    } else if (proxyProtocol.equalsIgnoreCase("https")) {
      return TigerProxyType.HTTPS;
    } else {
      throw new TigerUnknownProtocolException(
          "Protocol of type " + proxyProtocol + " not specified for proxies");
    }
  }

  public int calculateProxyPort() {
    if (port == null || port == -1) {
      if (type == TigerProxyType.HTTP) {
        return 80;
      } else if (type == TigerProxyType.HTTPS) {
        return 443;
      } else {
        return -1;
      }
    }
    return port;
  }

  public static String mapProxyPort(String proxyPort, TigerProxyType type) {
    if (proxyPort == null || proxyPort.equals("null") || proxyPort.equals("-1")) {
      if (type == TigerProxyType.HTTP) {
        return "80";
      } else if (type == TigerProxyType.HTTPS) {
        return "443";
      }
    }
    return proxyPort;
  }
}
