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

package de.gematik.rbellogger.testutil;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelValueFacet;
import de.gematik.rbellogger.util.RbelPathAble;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.*;

@Slf4j
public class RbelElementAssertion extends AbstractAssert<RbelElementAssertion, RbelElement> {

  private final RbelElement initial;

  public RbelElementAssertion(RbelElement actual) {
    super(actual, RbelElementAssertion.class);
    initial = actual;
    hasCorrectParentKeysSetInAllElements(actual);
  }

  private RbelElementAssertion(RbelElement actual, RbelElement initial) {
    super(actual, RbelElementAssertion.class);
    this.initial = initial;
    hasCorrectParentKeysSetInAllElements(actual);
  }

  public static RbelElementAssertion assertThat(RbelElement actual) {
    return new RbelElementAssertion(actual);
  }

  public RbelElementAssertion extractChildWithPath(String rbelPath) {
    final List<RbelElement> kids = actual.findRbelPathMembers(rbelPath);
    if (kids.isEmpty()) {
      failWithMessage(
          "Expected rbelPath %s to find member, but did not in tree %s",
          rbelPath, actual.printTreeStructureWithoutColors());
    }
    if (kids.size() > 1) {
      failWithMessage(
          "Expected rbelPath %s to find one member, but did return %s \n(%s) \nin tree %s",
          rbelPath,
          kids.size(),
          kids.stream().map(RbelPathAble::findNodePath).collect(Collectors.joining("\n")),
          actual.printTreeStructureWithoutColors());
    }
    return new RbelElementAssertion(kids.get(0), this.actual);
  }

  public RbelElementAssertion hasGivenValueAtPosition(String rbelPath, Object expectedValue) {
    extractChildWithPath(rbelPath).hasValueEqualTo(expectedValue);
    return this;
  }

  public RbelElementAssertion hasGivenFacetAtPosition(
      String rbelPath, Class<? extends RbelFacet> expectedFacet) {
    extractChildWithPath(rbelPath).hasFacet(expectedFacet);
    return this;
  }

  public RbelElementAssertion hasStringContentEqualToAtPosition(
      String rbelPath, String expectedValue) {
    extractChildWithPath(rbelPath).hasStringContentEqualTo(expectedValue);
    return this;
  }

  public RbelElementAssertion extractChildWithPath(String rbelPath, int index) {
    final List<RbelElement> kids = actual.findRbelPathMembers(rbelPath);
    if (kids.isEmpty()) {
      failWithMessage(
          "Expected rbelPath %s to find member, but did not in tree %s",
          rbelPath, actual.printTreeStructureWithoutColors());
    }
    if (kids.size() <= index) {
      failWithMessage(
          "Expected rbelPath %s to find %s member, but did return %s \n(%s) \nin tree %s",
          rbelPath,
          index,
          kids.size(),
          kids.stream().map(RbelPathAble::findNodePath).collect(Collectors.joining("\n")),
          actual.printTreeStructureWithoutColors());
    }
    return new RbelElementAssertion(kids.get(index), this.actual);
  }

  public RbelElementAssertion hasChildWithPath(String rbelPath) {
    extractChildWithPath(rbelPath);
    return this;
  }

  public RbelElementAssertion doesNotHaveChildWithPath(String rbelPath) {
    final List<RbelElement> kids = actual.findRbelPathMembers(rbelPath);
    if (!kids.isEmpty()) {
      failWithMessage("Expected rbelPath $s not to find anything, but found %s", rbelPath, kids);
    }
    return this.myself;
  }

  /**
   * Returns an assertion targeting the initial element of the assertion chain. Can be used to
   * perform chained assertions on multiple children of the same rbel element.
   *
   * <pre>{@code
   * RbelElementAssertion.assertThat(myElement)
   *   .extractChildWithPath("$.body.something")
   *   .hasStringContentEqualTo("foo")
   *   .andTheInitialElement()
   *   .extractChildWithPath("$.body.somethingelse")
   *   .hasStringContentEqualTo("bar");
   * }</pre>
   *
   * @return the assertion targeting the initial element of the current assertion chain
   */
  public RbelElementAssertion andTheInitialElement() {
    return new RbelElementAssertion(this.initial);
  }

  public RbelElementAssertion hasStringContentEqualTo(String expectedToString) {
    this.objects.assertHasToString(this.info, this.actual.getRawStringContent(), expectedToString);
    return this.myself;
  }

  public RbelElementAssertion hasNullContent() {
    if (actual.getRawContent() != null) {
      failWithMessage("Expecting null content, but found %s", actual.getRawStringContent());
    }
    return this.myself;
  }

  @Override
  public StringAssert asString() {
    return new StringAssert(actual.getRawStringContent());
  }

  public RbelElementAssertion hasFacet(Class<? extends RbelFacet> facetToTest) {
    if (!actual.hasFacet(facetToTest)) {
      failWithMessage(
          "Expecting element to have facet of type %s, but only found facets %s",
          facetToTest.getSimpleName(), new ArrayList<>(actual.getFacets()));
    }
    return this.myself;
  }

  public RbelElementAssertion doesNotHaveFacet(Class<? extends RbelFacet> facetToTest) {
    if (actual.hasFacet(facetToTest)) {
      failWithMessage(
          """
                Expecting element to have NOT facet of type %s, but it was found along with %s
                at element:
                $.%s
            """,
          facetToTest.getSimpleName(), new ArrayList<>(actual.getFacets()), actual.findNodePath());
    }
    return this.myself;
  }

  public RbelElementAssertion hasValueEqualTo(Object expected) {
    hasFacet(RbelValueFacet.class);
    final Object actualValue = actual.getFacetOrFail(RbelValueFacet.class).getValue();
    Assertions.assertThat(actualValue)
        .as("Checking value at position $.%s", actual.findNodePath())
        .isEqualTo(expected);
    return this.myself;
  }

  public <F extends RbelFacet> ObjectAssert<F> extractFacet(Class<F> facetClass) {
    if (!actual.hasFacet(facetClass)) {
      failWithMessage(
          "Expecting element to have facet of type %s, but only found facets %s",
          facetClass.getSimpleName(), new ArrayList<>(actual.getFacets()));
    }
    return new ObjectAssert<>(actual.getFacetOrFail(facetClass));
  }

  public RbelElementAssertion andPrintTree() {
    log.info(actual.printTreeStructure());
    return this;
  }

  public RbelElementAssertion matchesJexlExpression(String jexlExpression) {
    if (!TigerJexlExecutor.matchesAsJexlExpression(actual, jexlExpression)) {
      failWithMessage(
          "Expecting element to match jexl expression %s, but it did not. Element: %s",
          jexlExpression, actual.printTreeStructure());
    }
    return this;
  }

  public ListAssert<RbelElement> getChildrenWithPath(String rbelPath) {
    return new ListAssert<>(actual.findRbelPathMembers(rbelPath));
  }

  private void hasCorrectParentKeysSetInAllElements(RbelElement actual) {
    for (Entry<String, RbelElement> child : actual.getChildNodesWithKey().entries()) {
      if (child.getValue().getParentNode() != actual) {
        log.error(actual.findRootElement().printTreeStructure());
        failWithMessage(
            "Expecting all parents to be correct. Fail for child $.%s of element $.%s",
            child.getKey(), actual.findNodePath());
      }
      hasCorrectParentKeysSetInAllElements(child.getValue());
    }
  }

  public RbelElementAssertion hasCharset(Charset charset) {
    Assertions.assertThat(actual.getElementCharset())
        .as("Checking charset of element at position $.%s", actual.findNodePath())
        .isEqualTo(charset);
    return this;
  }
}
