/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.httpclient;

/*
 * @author jamesdbloom
 */

/**
 * This exception indicates there was an error while trying to communicate over a socket
 *
 * @author jamesdbloom
 */
public class SocketCommunicationException extends RuntimeException {
  public SocketCommunicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
