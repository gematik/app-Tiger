/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TigerConnectionStatus {
  CLOSED(0),
  OPEN_TCP(1),
  OPEN_TLS(2);

  private final int value;
}
