/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

/*
 * @author jamesdbloom
 */
public enum KeyMatchStyle {
  SUB_SET, // default
  MATCHING_KEY;

  public static KeyMatchStyle defaultValue = SUB_SET;
}
