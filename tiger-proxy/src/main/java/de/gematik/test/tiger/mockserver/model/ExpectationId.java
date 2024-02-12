/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import lombok.Data;
import lombok.experimental.Accessors;

/*
 * @author jamesdbloom
 */
@Data
@Accessors(chain = true, fluent = true)
public class ExpectationId extends ObjectWithJsonToString {

  private String id;
}
