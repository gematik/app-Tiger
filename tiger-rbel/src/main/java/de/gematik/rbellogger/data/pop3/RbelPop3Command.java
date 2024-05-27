/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.pop3;

import java.util.Arrays;
import java.util.Collections;

public enum RbelPop3Command {
  USER,
  PASS,
  QUIT,
  STAT,
  LIST,
  RETR,
  DELE,
  NOOP,
  RSET,
  APOP,
  TOP,
  UIDL;

  public static final int MAX_LENGTH =
      Collections.min(
          Arrays.stream(values()).map(RbelPop3Command::name).map(String::length).toList());
}
