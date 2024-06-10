/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode(callSuper = false)
@Data
public class SocketAddress extends ObjectWithJsonToString {
  private String host;
  private Integer port = 80;
  private Scheme scheme = Scheme.HTTP;

  public String getHost() {
    return host;
  }

  /**
   * The host or ip address to use when connecting to the socket to i.e. "www.mock-server.com"
   *
   * @param host a hostname or ip address as a string
   */
  public SocketAddress withHost(String host) {
    this.host = host;
    return this;
  }

  public Integer getPort() {
    return port;
  }

  /**
   * The port to use when connecting to the socket i.e. 80. If not specified the port defaults to
   * 80.
   *
   * @param port a port as an integer
   */
  public SocketAddress withPort(Integer port) {
    this.port = port;
    return this;
  }

  public Scheme getScheme() {
    return scheme;
  }

  /**
   * The scheme to use when connecting to the socket, either HTTP or HTTPS. If not specified the
   * scheme defaults to HTTP.
   *
   * @param scheme the scheme as a SocketAddress.Scheme value
   */
  public SocketAddress withScheme(Scheme scheme) {
    this.scheme = scheme;
    return this;
  }

  public enum Scheme {
    HTTP,
    HTTPS
  }
}
