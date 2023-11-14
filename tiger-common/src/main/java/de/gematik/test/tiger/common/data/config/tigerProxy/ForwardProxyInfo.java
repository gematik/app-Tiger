/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
