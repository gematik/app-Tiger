/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.key;

import java.security.Key;
import lombok.Getter;

@Getter
public class RbelVauKey extends RbelKey {

  private final RbelKey parentKey;

  public RbelVauKey(Key key, String keyName, int precedence, RbelKey parentKey) {
    super(key, keyName, precedence);
    this.parentKey = parentKey;
  }
}
