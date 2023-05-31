package de.gematik.rbellogger.testutil;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelValueFacet;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.api.StringAssert;

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
            failWithMessage("Expected rbelPath %s to find member, but did not in tree %s",
                rbelPath, actual.printTreeStructureWithoutColors());
        }
        if (kids.size() > 1) {
            failWithMessage("Expected rbelPath %s to find one member, but did return %s in tree %s",
                rbelPath, kids.size(), actual.printTreeStructureWithoutColors());
        }
        return new RbelElementAssertion(kids.get(0));
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

    public StringAssert asString() {
        return new StringAssert(actual.getRawStringContent());
    }

    public RbelElementAssertion hasFacet(Class<? extends RbelFacet> facetToTest) {
        if (!actual.hasFacet(facetToTest)) {
            failWithMessage("Expecting element to have facet of type %s, but only found facets %s",
                facetToTest.getSimpleName(), new ArrayList<>(actual.getFacets()));
        }
        return this.myself;
    }

    public RbelElementAssertion doesNotHaveFacet(Class<? extends RbelFacet> facetToTest) {
        if (actual.hasFacet(facetToTest)) {
            failWithMessage("Expecting element to have NOT facet of type %s, but it was found along with %s",
                facetToTest.getSimpleName(), new ArrayList<>(actual.getFacets()));
        }
        return this.myself;
    }

    public RbelElementAssertion hasValueEqualTo(Object expected) {
        hasFacet(RbelValueFacet.class);
        final Object actualValue = actual.getFacetOrFail(RbelValueFacet.class).getValue();
        if (!expected.equals(actualValue)) {
            failWithMessage("Expecting element to have value of %s, but found %s instead",
                expected, actualValue);
        }
        return this.myself;
    }

    public <F extends RbelFacet> ObjectAssert<F> extractFacet(Class<F> facetClass) {
        if (!actual.hasFacet(facetClass)) {
            failWithMessage("Expecting element to have facet of type %s, but only found facets %s",
                facetClass.getSimpleName(), new ArrayList<>(actual.getFacets()));
        }
        return new ObjectAssert<>(actual.getFacetOrFail(facetClass));
    }
}
