/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.lib.json.JsonChecker;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelValidator {

  public void assertAttributeOfCurrentResponseMatches(
      final String rbelPath,
      final String value,
      boolean shouldMatch,
      RbelMessageRetriever rbelMessageRetriever) {
    RbelMessageNodeElementMatchExecutor.builder()
        .rbelPath(rbelPath)
        .shouldMatch(shouldMatch)
        .oracle(value)
        .elements(rbelMessageRetriever.findElementsInCurrentResponse(rbelPath))
        .build()
        .execute();
  }

  public void assertAttributeOfCurrentRequestMatches(
      final String rbelPath,
      final String value,
      boolean shouldMatch,
      RbelMessageRetriever rbelMessageRetriever) {
    RbelMessageNodeElementMatchExecutor.builder()
        .rbelPath(rbelPath)
        .shouldMatch(shouldMatch)
        .oracle(value)
        .elements(rbelMessageRetriever.findElementsInCurrentRequest(rbelPath))
        .build()
        .execute();
  }

  public void assertAttributeOfCurrentResponseMatchesAs(
      String rbelPath,
      ModeType mode,
      String oracle,
      String diffOptionCsv,
      RbelMessageRetriever rbelMessageRetriever) {
    assertAttributeForMessagesMatchAs(
        mode, oracle, rbelMessageRetriever.findElementsInCurrentResponse(rbelPath), diffOptionCsv);
  }

  public void assertAttributeOfCurrentRequestMatchesAs(
      String rbelPath, ModeType mode, String oracle, RbelMessageRetriever rbelMessageRetriever) {
    assertAttributeForMessagesMatchAs(
        mode, oracle, rbelMessageRetriever.findElementsInCurrentRequest(rbelPath), "");
  }

  public void assertAttributeForMessagesMatchAs(
      ModeType mode, String oracle, List<RbelElement> elements, String diffOptionCSV) {
    HashMap<String, Throwable> exceptions = new HashMap<>();
    for (RbelElement element : elements) {
      try {
        mode.verify(oracle, element, diffOptionCSV);
        log.debug("Found matching element: \n{}", element.printTreeStructure());
        return;
      } catch (JsonChecker.JsonCheckerMismatchException | AssertionError e) {
        exceptions.put(element.getUuid(), e);
      }
    }
    if (elements.size() == 1) {
      RbelElement element = elements.get(0);
      throw new AssertionError(
          String.format(
              """
              Element value:
              %s
              Expected:
              %s
              Validation message:
              %s\
              """,
              element.getRawStringContent(), oracle, exceptions.get(element.getUuid())));
    } else {
      throw new AssertionError(
          String.format(
              "No matching element for value %s found in list of %d elements! ",
              oracle, elements.size()));
    }
  }
}
