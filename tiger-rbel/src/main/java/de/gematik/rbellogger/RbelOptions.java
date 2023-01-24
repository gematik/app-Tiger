/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger;

public class RbelOptions {
    public static boolean ACTIVATE_RBEL_PATH_DEBUGGING = false;
    public static int RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH = 3;
    public static int RBEL_PATH_TREE_VIEW_VALUE_OUTPUT_LENGTH = 50;
    public static boolean ACTIVATE_JEXL_DEBUGGING = false;
    public static boolean ACTIVATE_FACETS_PRINTING = true;

    public static void activateJexlDebugging() {
        ACTIVATE_JEXL_DEBUGGING = true;
    }

    public static void deactivateJexlDebugging() {
        ACTIVATE_JEXL_DEBUGGING = false;
    }

    public static void activateRbelPathDebugging() {
        ACTIVATE_RBEL_PATH_DEBUGGING = true;
    }

    public static void deactivateRbelPathDebugging() {
        ACTIVATE_RBEL_PATH_DEBUGGING = false;
    }

    public static void activateFacetsPrinting() {
        ACTIVATE_FACETS_PRINTING = true;
    }

    public static void deactivateFacetsPrinting() {
        ACTIVATE_FACETS_PRINTING = false;
    }

    public static void reset() {
        ACTIVATE_RBEL_PATH_DEBUGGING = false;
        RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH = 3;
        RBEL_PATH_TREE_VIEW_VALUE_OUTPUT_LENGTH = 50;
        ACTIVATE_JEXL_DEBUGGING = false;
        ACTIVATE_FACETS_PRINTING = true;
    }
}
