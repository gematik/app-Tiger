/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.key;

import lombok.Getter;

import java.security.Key;
import java.util.List;

@Getter
public class RbelVauKey extends RbelKey {

    private final RbelKey parentKey;

    public RbelVauKey(Key key, String keyName, int precedence, RbelKey parentKey) {
        super(key, keyName, precedence);
        this.parentKey = parentKey;
    }
}
