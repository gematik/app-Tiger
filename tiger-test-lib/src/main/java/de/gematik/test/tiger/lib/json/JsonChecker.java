/*
 * Copyright (c) 2022 gematik GmbH
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
            json = convertToJsonObject(jsonStr);
            oracle = convertToJsonObject(oracleStr);
            for (String oracleKey : oracle.keySet()) {
                if (oracleKey.startsWith("____")) {
                    continue;
                }
                if (!json.has(oracleKey)) {
                    throw new JsonCheckerAssertionError("Expected JSON to have key '" + oracleKey
                        + "', but only found keys '" + json.keySet() + "'");
                }
            }

            if (checkExtraAttributes) {
                // check json keys are all in oracle (either as name or as ____name
                final JSONObject finalOracle = oracle;
                json.keySet().forEach(
                    key -> assertThat(finalOracle.has(key) || finalOracle.has("____" + key))
                        .withFailMessage("EXTRA Key " + key + " detected in received in JSON").isTrue()
                );
            }

            compareAllAttributes(json, oracle);
        } catch (final NoSuchMethodError nsme) {
            Assertions.fail(dumpComparisonBetween(
                "JSON does not match!\nExpected:\n%s\n\n--------\n\nReceived:\n%s",
                null, oracle == null ? "Oracle is null" : oracle.toString(2),
                json == null ? "Received is null" : json.toString(2)), nsme);
        }
    }

    private JSONObject convertToJsonObject(String jsonStr) {
        try {
            return new JSONObject(jsonStr);
        } catch (RuntimeException e) {
            throw new JsonCheckerConversionException(jsonStr);
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
            if (IGNORE_JSON_VALUE.equals(oracleValue)) {
                continue;
            }
            if (!json.get(jsonKey).getClass()
                .equals(oracle.get(oracleKey).getClass())) {
                throw new JsonCheckerAssertionError("Expected an '"
                    + oracle.get(jsonKey).getClass().getSimpleName()
                    + "' at key '" + jsonKey + "', but found '"
                    + json.get(jsonKey).getClass().getSimpleName() + "'");
            }

            if (oracle.get(oracleKey) instanceof JSONObject) {
                assertJsonObjectShouldMatchOrContainInAnyOrder(json.get(jsonKey).toString(),
                    oracle.get(oracleKey).toString(), true);
            } else if (oracle.get(oracleKey) instanceof JSONArray) {
                JSONAssert.assertEquals(oracle.get(oracleKey).toString(), json.get(jsonKey).toString(),
                    customComparator);
            } else {
                final var jsoValue = json.get(jsonKey).toString();
                if (!jsoValue.equals(oracleValue)) {
                    try {
                        assertThat(jsoValue)
                            .withFailMessage(dumpComparisonAtKeyDiffer(oracleKey, oracleValue, jsoValue))
                            .matches(oracleValue);
                    } catch (final RuntimeException ex) {
                        Assertions.fail(dumpComparisonAtKeyDiffer(oracleKey, oracleValue, jsoValue));
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
    }    final CustomComparator customComparator = new CustomComparator(JSONCompareMode.LENIENT,
        new Customization("***", (oracleJson, testJson) -> {
            try {
                new JSONObject(testJson.toString());
                new JSONObject(oracleJson.toString());
                assertJsonObjectShouldMatchOrContainInAnyOrder(testJson.toString(), oracleJson.toString(), true);
            } catch (final RuntimeException e) {
                try {
                    new JSONArray(testJson.toString());
                    new JSONArray(oracleJson.toString());
                    assertJsonArrayShouldMatchInAnyOrder(testJson.toString(), oracleJson.toString());
                } catch (final RuntimeException e2) {
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
            super.compareJSONArrayOfJsonObjects(key, expected, actual, result);
        }
    };

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

    static class JsonCheckerConversionException extends RuntimeException {

        public JsonCheckerConversionException(String failingJsonString) {
            super("Exception while trying to convert '" + failingJsonString + "' to JSON-Object");
        }
    }

    static class JsonCheckerAssertionError extends AssertionError {

        public JsonCheckerAssertionError(String s) {
            super(s);
        }
    }
}
