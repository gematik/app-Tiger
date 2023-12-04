/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger;

import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import lombok.Getter;

public class RbelOptions {

  @Getter private static boolean activateRbelPathDebugging = false;
  @Getter private static int rbelPathTreeViewMinimumDepth = 3;
  @Getter private static int rbelPathTreeViewValueOutputLength = 50;
  @Getter private static boolean activateJexlDebugging = false;
  @Getter private static boolean activateFacetsPrinting = true;

  private RbelOptions() {}

  public static void activateJexlDebugging() {
    activateJexlDebugging = true;
    TigerJexlExecutor.setActivateJexlDebugging(true);
  }

  public static void deactivateJexlDebugging() {
    activateJexlDebugging = false;
    TigerJexlExecutor.setActivateJexlDebugging(false);
  }

  public static void activateRbelPathDebugging() {
    activateRbelPathDebugging = true;
  }

  public static void deactivateRbelPathDebugging() {
    activateRbelPathDebugging = false;
  }

  public static void activateFacetsPrinting() {
    activateFacetsPrinting = true;
  }

  public static void deactivateFacetsPrinting() {
    activateFacetsPrinting = false;
  }

  public static void reset() {
    activateRbelPathDebugging = false;
    TigerJexlExecutor.setActivateJexlDebugging(false);
    rbelPathTreeViewMinimumDepth = 3;
    rbelPathTreeViewValueOutputLength = 50;
    activateJexlDebugging = false;
    activateFacetsPrinting = true;
  }
}
