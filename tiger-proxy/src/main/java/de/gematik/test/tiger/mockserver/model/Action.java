/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import lombok.Data;

/*
 * @author jamesdbloom
 */
@Data
public abstract class Action extends ObjectWithJsonToString {
  private String expectationId;
}
