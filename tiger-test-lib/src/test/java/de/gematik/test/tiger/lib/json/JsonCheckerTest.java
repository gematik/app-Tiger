package de.gematik.test.tiger.lib.json;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class JsonCheckerTest {

    final JsonChecker check = new JsonChecker();

    @Test
    public void testOptionalJSONAttributeFlatOKMissing() {
        check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: 'val1' }",
            "{ attr1: 'val1', ____attr2: 'val2' }", true);
    }

    @Test
    public void testOptionalJSONAttributeFlatOKEquals() {
        check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1:'val1', attr2:'val2' }",
            "{ attr1: 'val1', ____attr2: 'val2' }", true);
    }

    @Test
    public void testOptionalJSONAttributeFlatOKMatches() {
        check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1:'val1', attr2:'val2' }",
            "{ attr1: 'val1', ____attr2: 'v.*' }", true);
    }

    @Test
    public void testOptionalJSONAttributeFlatOKNotEquals() {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1:'val1', attr2:'val2' }",
            "{ attr1: 'val1', ____attr2: 'valXXX' }", true))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    public void testOptionalJSONAttributeFlatOKMismatch() {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1:'val1', attr2:'val2' }",
            "{ attr1: 'val1', ____attr2: 'v?\\\\d' }", true))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeFlatOExtraAttr() {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: 'val1', attr3: 'val3' }",
            "{ attr1: 'val1', ____attr2: 'val2' }", true))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeFlatOExtraAttrNoCheck() {
        check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: 'val1', attr3: 'val3' }",
            "{ attr1: 'val1', ____attr2: 'val2' }", false);
    }


    @Test
    public void testJSONAttributeInvalidJSONOracle() {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: 'val1', attr3: 'val3' }",
            "{ attr1: 'val1', ____attr2: 'val2' ::::  }", false))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeInvalidJSONReceived() {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: 'val1', attr3: 'val3' :::: }",
            "{ attr1: 'val1', ____attr2: 'val2'  }", false))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeShouldMatchOKEqual() {
        check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "val3"
        );
    }

    @Test
    public void testJSONAttributeShouldMatchOKNull() {
        check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr3: null }"),
            "attr3",
            null
        );
    }

    @Test
    public void testJSONAttributeShouldMatchOKNullNot() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            null
        )).isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeShouldMatchOKREMOVE() {
        check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr5: 'val3' }"),
            "attr3",
            "$REMOVE"
        );
    }

    @Test
    public void testJSONAttributeShouldMatchOKREMOVENOT() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "$REMOVE"
        )).isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeShouldMatchOKMatch() {
        check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "v.*"
        );
    }

    @Test
    public void testJSONAttributeShouldMatchOKNotEqual() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "val3XXXX")).isInstanceOf(AssertionError.class);
    }
    // TODO $REMOVE

    @Test
    public void testJSONAttributeShouldMatchOKMismatch() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "xxx.*")).isInstanceOf(AssertionError.class);
    }







    @Test
    public void testJSONAttributeShouldNotMatchOKEqual() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "val3"
        )).isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeShouldNotMatchOKNull() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr3: null }"),
            "attr3",
            null
        )).isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeShouldNotMatchOKNullNot() {
        check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            null
        );
    }

    @Test
    public void testJSONAttributeShouldNotMatchOKREMOVE() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr5: 'val3' }"),
            "attr3",
            "$REMOVE"
        )).isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeShouldNotMatchOKREMOVENOT() {
        check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "$REMOVE"
        );
    }

    @Test
    public void testJSONAttributeShouldNotMatchOKMatch() {
        assertThatThrownBy(() -> check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "v.*"
        )).isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeShouldNotMatchOKNotEqual() {
        check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "val3XXXX");
    }

    @Test
    public void testJSONAttributeShouldNotMatchOKMismatch() {
        check.assertJsonAttributeShouldNotMatch(
            new JSONObject("{ attr1: 'val1', attr3: 'val3' }"),
            "attr3",
            "xxx.*");
    }

    // TODO multilevel JSON Objects
    @Test
    public void testJSONAttributeMultiLvlOKEquals() {
        check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
            "{ attr1: { sub1: 'val1' }, attr2: 'val2' }", true);
    }

    @Test
    public void testJSONAttributeMultiLvlOKMatches() {
        check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
            "{ attr1: { sub1: 'v.*1' }, attr2: 'val2' }", true);
    }

    @Test
    public void testJSONAttributeMultiLvlOKNotEquals() {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
            "{ attr1: { sub1: 'val1xxxx' }, attr2: 'val2' }", true))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    public void testJSONAttributeMultiLvlOKMismatch() {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
            "{ attr1: { sub1: 'xxx.*' }, attr2: 'val2' }", true))
            .isInstanceOf(AssertionError.class);
    }



    // TODO JSONArrays as top struct with primitives
    // TODO multilvl JSON Objects with JSONArrays
    // TODO JSONArrays as top struct with multilvl JSONObjects/JSONArrays
}
