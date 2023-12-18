/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelValueFacet;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.*;

public class RbelElementAssertion extends AbstractAssert<RbelElementAssertion, RbelElement> {

  public RbelElementAssertion(RbelElement actual) {
    super(actual, RbelElementAssertion.class);
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
          "Expected rbelPath %s to find one member, but did return %s in tree %s",
          rbelPath, kids.size(), actual.printTreeStructureWithoutColors());
    }
    return new RbelElementAssertion(kids.get(0));
  }

  public RbelElementAssertion doesNotContainChildWithPath(String rbelPath) {
    final List<RbelElement> kids = actual.findRbelPathMembers(rbelPath);
    if (!kids.isEmpty()) {
      failWithMessage(
          "Expected rbelPath %s to not find any member, but did so in tree %s",
          rbelPath, actual.printTreeStructureWithoutColors());
    }
    return this.myself;
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

  public ByteArrayAssert getContent() {
    return new ByteArrayAssert(actual.getRawContent());
  }

  public OptionalAssert<String> valueAsString() {
    return AssertionsForClassTypes.assertThat(actual.printValue());
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
          "Expecting element to have NOT facet of type %s, but it was found along with %s",
          facetToTest.getSimpleName(), new ArrayList<>(actual.getFacets()));
    }
    return this.myself;
  }

  public RbelElementAssertion hasValueEqualTo(Object expected) {
    hasFacet(RbelValueFacet.class);
    final Object actualValue = actual.getFacetOrFail(RbelValueFacet.class).getValue();
    if (!expected.equals(actualValue)) {
      failWithMessage(
          "Expecting element to have value of %s, but found %s instead", expected, actualValue);
    }
    return this.myself;
  }
}
