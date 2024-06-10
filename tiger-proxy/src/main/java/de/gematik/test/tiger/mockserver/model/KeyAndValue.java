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
public class KeyAndValue extends ObjectWithJsonToString {
  private final String name;
  private final String value;
}
