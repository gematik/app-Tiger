/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger;

import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;

public class RbelOptions {
    public static boolean ACTIVATE_RBEL_PATH_DEBUGGING = false;
    public static int RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH = 3;
    public static int RBEL_PATH_TREE_VIEW_VALUE_OUTPUT_LENGTH = 50;
    public static boolean ACTIVATE_JEXL_DEBUGGING = false;
    public static boolean ACTIVATE_FACETS_PRINTING = true;

    public static void activateJexlDebugging() {
        ACTIVATE_JEXL_DEBUGGING = true;
        TigerJexlExecutor.ACTIVATE_JEXL_DEBUGGING = true;
    }

    public static void deactivateJexlDebugging() {
        ACTIVATE_JEXL_DEBUGGING = false;
        TigerJexlExecutor.ACTIVATE_JEXL_DEBUGGING = false;
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
        TigerJexlExecutor.ACTIVATE_JEXL_DEBUGGING = false;
        RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH = 3;
        RBEL_PATH_TREE_VIEW_VALUE_OUTPUT_LENGTH = 50;
        ACTIVATE_JEXL_DEBUGGING = false;
        ACTIVATE_FACETS_PRINTING = true;
    }
}