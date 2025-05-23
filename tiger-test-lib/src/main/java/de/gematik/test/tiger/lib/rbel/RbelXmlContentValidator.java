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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.xml.RbelXmlFacet;
import java.util.*;
import java.util.function.UnaryOperator;
import javax.xml.transform.Source;
import lombok.SneakyThrows;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;

public class RbelXmlContentValidator implements RbelContentValidator {
  private static final Map<String, UnaryOperator<DiffBuilder>> DIFF_OPTIONS = new HashMap<>();

  static {
    DIFF_OPTIONS.put("nocomment", DiffBuilder::ignoreComments);
    DIFF_OPTIONS.put("txtignoreempty", DiffBuilder::ignoreElementContentWhitespace);
    DIFF_OPTIONS.put("txttrim", DiffBuilder::ignoreWhitespace);
    DIFF_OPTIONS.put("txtnormalize", DiffBuilder::normalizeWhitespace);
  }

  @Override
  public void verify(final String oracle, final RbelElement el, final String diffOptionCSV) {
    assertThat(el.hasFacet(RbelXmlFacet.class))
        .withFailMessage("Node " + el.getKey() + " is not XML")
        .isTrue();
    compareXMLStructure(oracle, el.getRawStringContent(), diffOptionCSV);
  }

  @SneakyThrows
  public void compareXMLStructure(
      final String oracle, final String test, final String diffOptionCSV) {
    final List<UnaryOperator<DiffBuilder>> diffOptions = new ArrayList<>();
    Arrays.stream(diffOptionCSV.split(","))
        .map(String::trim)
        .forEach(
            srcClassId -> {
              assertThat(DIFF_OPTIONS).containsKey(srcClassId);
              diffOptions.add(DIFF_OPTIONS.get(srcClassId));
            });
    compareXMLStructure(oracle, test, diffOptions);
  }

  public void compareXMLStructure(
      final String test, final String oracle, final List<UnaryOperator<DiffBuilder>> diffOptions) {
    final Source srcTest = Input.from(test).build();
    final Source srcOracle = Input.from(oracle).build();
    DiffBuilder db = DiffBuilder.compare(srcOracle).withTest(srcTest);
    for (final UnaryOperator<DiffBuilder> src : diffOptions) {
      db = src.apply(db);
    }

    db = db.checkForSimilar();
    db.withDifferenceEvaluator(
        (comparison, outcome) -> {
          if (outcome != ComparisonResult.EQUAL
              && (comparison.getType() == ComparisonType.NAMESPACE_URI
                  || comparison.getType() == ComparisonType.NAMESPACE_PREFIX)) {
            return ComparisonResult.SIMILAR;
          }
          return outcome;
        });

    final Diff diff = db.build();
    assertThat(diff.hasDifferences()).withFailMessage("XML tree mismatch!\n" + diff).isFalse();
  }

  public void compareXMLStructure(final String test, final String oracle) {
    compareXMLStructure(test, oracle, Collections.emptyList());
  }
}
