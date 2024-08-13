/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger;

import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelOptions {

  @Getter private static boolean activateRbelPathDebugging = false;
  @Getter private static int rbelPathTreeViewMinimumDepth = 3;
  @Getter private static int rbelPathTreeViewValueOutputLength = 50;
  @Getter private static boolean activateJexlDebugging = false;
  @Getter private static boolean activateFacetsPrinting = true;

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
