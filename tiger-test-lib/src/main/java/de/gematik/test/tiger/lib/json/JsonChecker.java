/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.json;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Iterator;
import lombok.SneakyThrows;
import net.thucydides.core.annotations.Step;
import org.apache.commons.collections.IteratorUtils;
import org.assertj.core.api.Assertions;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

/**
 * values will be first checked for containing "${json-unit.ignore}" then for equals and finally for regex matches
 * <p>
 * JSON object attributes starting with four underscores "____" are optional and allow the oracle string to contain
 * attributes to be checked for value ONLY if it exists in the test JSON
 * <p>
 * TODO TGR-256 check JSONObject as parameter yields unreadable output in serenity output, maybe reintroduce
 * SerenityJSONObject
 */
public class JsonChecker {

    public static final String IGNORE_JSON_VALUE = "${json-unit.ignore}";

    final CustomComparator customComparator = new CustomComparator(JSONCompareMode.LENIENT,
        new Customization("***", (oracleJson, testJson) -> {
            try {
                new JSONObject(testJson.toString());
                new JSONObject(oracleJson.toString());
                assertJsonObjectShouldMatchOrContainInAnyOrder(testJson.toString(), oracleJson.toString(), true);
            } catch (final Exception e) {
                try {
                    new JSONArray(testJson.toString());
                    new JSONArray(oracleJson.toString());
                    assertJsonArrayShouldMatchInAnyOrder(testJson.toString(), oracleJson.toString());
                } catch (final Exception e2) {
                    return oracleJson.toString().equals(IGNORE_JSON_VALUE) ||
                        testJson.toString().equals(oracleJson.toString()) ||
                        testJson.toString().matches(oracleJson.toString());
                }
            }
            return true;
        })) {
        @Override
        protected void compareJSONArrayOfJsonObjects(final String key, final JSONArray expected,
            final JSONArray actual, final JSONCompareResult result) throws JSONException {
            if (expected.length() == 1 && actual.length() == 1) {
                compareJSON(expected.getJSONObject(0), actual.getJSONObject(0));
                return;
            }
            // TODO TGR-254 LOW PRIO make it without unique key based approach
            super.compareJSONArrayOfJsonObjects(key, expected, actual, result);
        }
    };

    @Step
    @SneakyThrows
    public void assertJsonArrayShouldMatchInAnyOrder(final String json, final String oracle) {
        JSONAssert.assertEquals(oracle, json, new CustomComparator(
            JSONCompareMode.LENIENT, new Customization("***", (oracleJson, testJson) -> {
            if (testJson instanceof JSONObject) {
                assertJsonObjectShouldMatchOrContainInAnyOrder(testJson.toString(), oracleJson.toString(), true);
                return true;
            } else if (testJson instanceof JSONArray) {
                assertJsonArrayShouldMatchInAnyOrder(testJson.toString(), oracleJson.toString());
                return true;
            } else {
                // return true if its json ignore value in oracle or if values are equal
                return (IGNORE_JSON_VALUE.equals(oracleJson) || testJson.toString().equals(oracleJson.toString()))
                    // else check if the values match
                    || testJson.toString().matches(oracleJson.toString());
            }
        })));
    }

    @Step
    public void assertJsonObjectShouldMatchOrContainInAnyOrder(final String jsonStr, final String oracleStr,
        boolean checkExtraAttributes) {
        JSONObject json = null;
        JSONObject oracle = null;

        try {
            json = new JSONObject(jsonStr);
            oracle = new JSONObject(oracleStr);
            Assertions.assertThat(IteratorUtils.toArray(json.keys(), String.class))
                .contains(IteratorUtils.toList(oracle.keys()).stream()
                    .filter(key -> !key.toString().startsWith("____"))
                    .toArray());

            if (checkExtraAttributes) {
                // check json keys are all in oracle (either as name or as ____name
                final JSONObject finalOracle = oracle;
                json.keySet().forEach(
                    key -> assertThat(finalOracle.has(key.toString()) || finalOracle.has("____" + key))
                        .withFailMessage("EXTRA Key " + key + " detected in received in JSON").isTrue()
                );
            }

            compareAllAttributes(json, oracle);
        } catch (JSONException jsoex) {
            throw new AssertionError(
                "Failed to convert " + (oracle == null ? "oracle" : "received") + " '" + (oracle == null ? oracleStr
                    : jsonStr) + "' to JSON", jsoex);
        } catch (final NoSuchMethodError nsme) {
            Assertions.fail(dumpComparisonBetween(
                "JSON does not match!\nExpected:\n%s\n\n--------\n\nReceived:\n%s",
                null, oracle == null ? "Oracle is null" : oracle.toString(2),
                json == null ? "Received is null" : json.toString(2)), nsme);
        }
    }

    private void compareAllAttributes(JSONObject json, JSONObject oracle) {
        final Iterator<String> keyIt = oracle.keys();
        while (keyIt.hasNext()) {
            final String oracleKey = keyIt.next();
            final boolean optionalAttribute = oracleKey.startsWith("____");
            final String jsonKey = optionalAttribute ? oracleKey.substring(4) : oracleKey;
            final var oracleValue = oracle.get(oracleKey).toString();
            if ((optionalAttribute && !json.has(jsonKey))
                || ("$NULL".equals(oracleValue) && json.get(jsonKey) == JSONObject.NULL)) {
                continue;
            }
            if (!IGNORE_JSON_VALUE.equals(oracleValue)) {
                if (json.get(jsonKey) instanceof JSONObject) {
                    assertJsonObjectShouldMatchOrContainInAnyOrder(json.get(jsonKey).toString(),
                        oracle.get(oracleKey).toString(), true);
                } else if (json.get(jsonKey) instanceof JSONArray) {
                    // TODO TGR-254 shouldn't this call assertJsonArrayShouldMatchInAnyOrder?
                    JSONAssert.assertEquals(oracle.get(oracleKey).toString(), json.get(jsonKey).toString(),
                        customComparator);
                } else {
                    final var jsoValue = json.get(jsonKey).toString();
                    if (!jsoValue.equals(oracleValue)) {
                        try {
                            assertThat(jsoValue)
                                .withFailMessage(dumpComparisonAtKeyDiffer(oracleKey, oracleValue, jsoValue))
                                .matches(oracleValue);
                        } catch (final Exception ex) {
                            Assertions.fail(dumpComparisonAtKeyDiffer(oracleKey, oracleValue, jsoValue));
                        }
                    }
                }
            }
        }
    }

    @Step
    @SneakyThrows
    public void assertJsonAttributeShouldMatch(final JSONObject json, final String claimName,
        final String regex) {

        if (regex != null && regex.equals("$REMOVE")) {
            Assertions.assertThat(IteratorUtils.toArray(json.keys())).doesNotContain(claimName);
            return;
        }
        Assertions.assertThat(IteratorUtils.toArray(json.keys())).contains(claimName);

        if (regex == null) {
            if (json.get(claimName) != JSONObject.NULL) {
                assertThat(json.get(claimName)).isNull();
            }
            return;
        }
        var jsoValue = json.get(claimName).toString();
        if (!jsoValue.equals(regex)) {
            assertThat(jsoValue).withFailMessage(dumpComparisonAtKeyDiffer(claimName, regex, jsoValue))
                .matches(regex);
        }
    }

    @Step
    @SneakyThrows
    public void assertJsonAttributeShouldNotMatch(final JSONObject json, final String claimName,
        final String regex) {

        Assertions.assertThat(IteratorUtils.toArray(json.keys())).contains(claimName);

        if (regex == null) {
            assertThat(json.get(claimName)).isNotEqualTo(JSONObject.NULL);
            assertThat(json.get(claimName)).isNotNull();
        } else {

            var jsoValue = json.get(claimName).toString();
            if (!jsoValue.equals(regex)) {
                assertThat(jsoValue).withFailMessage(
                        dumpComparisonAtKeyDiffer(claimName, regex, jsoValue))
                    .doesNotMatch(regex);
            } else {
                Assertions.fail(dumpComparisonAtKeyDiffer(claimName, regex, jsoValue));
            }
        }
    }

    private String dumpComparisonAtKeyDiffer(String key, String expected, String received) {
        return dumpComparisonBetween("JSON object does match at key '%s'\nExpected:\n%s\n\n--------\n\nReceived:\n%s",
            key, expected, received);
    }

    private String dumpComparisonBetween(String pattern, String key, String expected, String received) {
        if (key != null) {
            return String.format(pattern, key, expected, received);
        } else {
            return String.format(pattern, expected, received);
        }
    }
}
