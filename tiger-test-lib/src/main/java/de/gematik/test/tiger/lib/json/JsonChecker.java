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
import groovy.util.logging.Slf4j;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import net.thucydides.core.annotations.Step;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.skyscreamer.jsonassert.*;
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
@Slf4j
public class JsonChecker {

    public static final String IGNORE_JSON_VALUE = "${json-unit.ignore}";
    private static final String OPTIONAL_MARKER = "____";
    private static final String NULL_MARKER = "$NULL";

    @Step
    public void compareJsonStrings(final String jsonStr, final String oracleStr,
        boolean checkExtraAttributes) {
        JSONTokener jsonTokener = new JSONTokener(jsonStr);
        JSONTokener oracleTokener = new JSONTokener(oracleStr);

        final Object jsonValue = jsonTokener.nextValue();
        final Object oracleValue = oracleTokener.nextValue();
        compareJsonStrings(jsonValue, oracleValue, checkExtraAttributes);
    }

    public void compareJsonStrings(final Object jsonValue, final Object oracleValue,
        boolean checkExtraAttributes) {
        if ((NULL_MARKER.equals(oracleValue) && jsonValue == JSONObject.NULL)
            || (NULL_MARKER.equals(jsonValue) && oracleValue == JSONObject.NULL)) {
            return;
        }
        if (!jsonValue.getClass().equals(oracleValue.getClass())) {
            throw new JsonCheckerAssertionError(String.format("Could not compare %s to %s: Different types!",
                jsonValue.getClass().getSimpleName(), oracleValue.getClass().getSimpleName()));
        }

        if (jsonValue instanceof JSONObject) {
            assertJsonObjectShouldMatchOrContainInAnyOrder(
                (JSONObject) jsonValue, (JSONObject) oracleValue, checkExtraAttributes);
        } else if (jsonValue instanceof JSONArray) {
            assertJsonArrayShouldMatchInAnyOrder(jsonValue.toString(), oracleValue.toString(), checkExtraAttributes);
        } else {
            compareValues(jsonValue, oracleValue);
        }
    }

    @Step
    public void assertJsonArrayShouldMatchInAnyOrder(final String json, final String oracle) {
        assertJsonArrayShouldMatchInAnyOrder(json, oracle, true);
    }

    public void assertJsonArrayShouldMatchInAnyOrder(final String json, final String oracle,
        boolean checkExtraAttributes) {
        JSONAssert.assertEquals(oracle, json, new CustomComparator(
            JSONCompareMode.LENIENT, new Customization("***", (testJson, oracleJson) -> {
            if (testJson instanceof JSONObject) {
                assertJsonObjectShouldMatchOrContainInAnyOrder(testJson.toString(), oracleJson.toString(),
                    checkExtraAttributes);
                return true;
            } else if (testJson instanceof JSONArray) {
                assertJsonArrayShouldMatchInAnyOrder(testJson.toString(), oracleJson.toString(), checkExtraAttributes);
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
        final JSONObject json = convertToJsonObject(jsonStr);
        final JSONObject oracle = convertToJsonObject(oracleStr);

        assertJsonObjectShouldMatchOrContainInAnyOrder(json, oracle, checkExtraAttributes);
    }

    public void assertJsonObjectShouldMatchOrContainInAnyOrder(final JSONObject json, final JSONObject oracle,
        boolean checkExtraAttributes) {

        try {
            for (String oracleKey : oracle.keySet()) {
                if (keyNotContainedInSetOrOptional(oracleKey, json.keySet())) {
                    throw new JsonCheckerAssertionError("Expected JSON to have key '" + oracleKey
                        + "', but only found keys '" + json.keySet() + "'");
                }
            }

            if (checkExtraAttributes) {
                // check json keys are all in oracle (either as name or as ____name
                final Optional<JsonCheckerAssertionError> checkerAssertionError = json.keySet().stream()
                    .filter(key -> keyNotContainedInSetOrOptional(key, oracle.keySet()))
                    .findAny()
                    .map(key -> new JsonCheckerAssertionError("EXTRA Key " + key + " detected in received in JSON"));
                if (checkerAssertionError.isPresent()) {
                    throw checkerAssertionError.get();
                }
            }

            compareAllAttributes(json, oracle);
        } catch (final NoSuchMethodError nsme) {
            Assertions.fail(dumpComparisonBetween(
                "JSON does not match!\nExpected:\n%s\n\n--------\n\nReceived:\n%s",
                oracle.toString(2), json.toString(2)), nsme);
        }
    }

    private boolean keyNotContainedInSetOrOptional(String oracleKey, Set<String> keySet) {
        if (oracleKey.startsWith(OPTIONAL_MARKER)) {
            return false;
        }

        return keySet.stream()
            .map(key -> StringUtils.stripStart(key, OPTIONAL_MARKER))
            .noneMatch(oracleKey::equals);
    }

    private Optional<Object> findTargetByKey(JSONObject json, String jsonKey) {
        return json.keySet().stream()
            .filter(key -> StringUtils.stripStart(key, OPTIONAL_MARKER).equals(
                StringUtils.stripStart(jsonKey, OPTIONAL_MARKER)))
            .map(json::get)
            .findAny();
    }

    private JSONObject convertToJsonObject(String jsonStr) {
        try {
            return new JSONObject(jsonStr);
        } catch (RuntimeException e) {
            throw new JsonCheckerConversionException(jsonStr, e);
        }
    }

    private void compareAllAttributes(JSONObject json, JSONObject oracle) {
        final Iterator<String> keyIt = oracle.keys();
        while (keyIt.hasNext()) {
            final String oracleKey = keyIt.next();
            final Optional<Object> jsonTargetOptional = findTargetByKey(json, oracleKey);
            final Optional<Object> oracleTargetOptional = findTargetByKey(oracle, oracleKey);
            if ((jsonTargetOptional.isEmpty() && oracleKey.startsWith(OPTIONAL_MARKER))
                || (oracleTargetOptional.isEmpty() && oracleKey.startsWith(OPTIONAL_MARKER))) {
                continue;
            }
            final Object jsonTarget = jsonTargetOptional.orElseThrow(() -> new JsonCheckerAssertionError(
                "Could not find attribute by key '" + oracleKey + "' in '" + json + "'"));
            final Object oracleTarget = oracleTargetOptional.orElseThrow(() -> new JsonCheckerAssertionError(
                "Could not find attribute by key '" + oracleKey + "' in '" + oracle + "'"));
            try {
                compareValues(jsonTarget, oracleTarget);
            } catch (AssertionError e) {
                throw new JsonCheckerAssertionError("Comparison failed at key '" + oracleKey + "'", e);
            }
        }
    }

    private void compareValues(Object jsonTarget, Object oracleTarget) {
        if ((NULL_MARKER.equals(oracleTarget) && jsonTarget == JSONObject.NULL)
            || (NULL_MARKER.equals(jsonTarget) && oracleTarget == JSONObject.NULL)) {
            return;
        }
        if (IGNORE_JSON_VALUE.equals(oracleTarget)) {
            return;
        }
        if (!jsonTarget.getClass().equals(oracleTarget.getClass())) {
            throw new JsonCheckerAssertionError("Expected an '"
                + oracleTarget.getClass().getSimpleName()
                + "', but found '"
                + jsonTarget.getClass().getSimpleName() + "'");
        }

        if (oracleTarget instanceof JSONObject) {
            assertJsonObjectShouldMatchOrContainInAnyOrder(jsonTarget.toString(),
                oracleTarget.toString(), true);
        } else if (oracleTarget instanceof JSONArray) {
            JSONAssert.assertEquals(oracleTarget.toString(), jsonTarget.toString(),
                customComparator);
        } else {
            final var jsoValue = jsonTarget.toString();
            if (!jsoValue.equals(oracleTarget)) {
                try {
                    assertThat(jsoValue)
                        .withFailMessage(dumpComparisonAtKeyDiffer(oracleTarget.toString(), jsoValue))
                        .matches(oracleTarget.toString());
                } catch (final RuntimeException ex) {
                    Assertions.fail(dumpComparisonAtKeyDiffer(oracleTarget.toString(), jsoValue));
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
            try {
                assertThat(jsoValue)
                    .withFailMessage(dumpComparisonAtKeyDiffer(regex, jsoValue))
                    .matches(regex);
            } catch (AssertionError e) {
                throw new JsonCheckerAssertionError("Assertion failed at key '" + claimName + "'", e);
            }
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
                        dumpComparisonAtKeyDiffer(regex, jsoValue))
                    .doesNotMatch(regex);
            } else {
                Assertions.fail(dumpComparisonAtKeyDiffer(regex, jsoValue));
            }
        }
    }

    final CustomComparator customComparator = new JsonCheckerComparator();

    private String dumpComparisonAtKeyDiffer(String expected, String received) {
        return dumpComparisonBetween("JSON object does match\nExpected:\n%s\n\n--------\n\nReceived:\n%s",
            expected, received);
    }

    private String dumpComparisonBetween(String pattern, String expected, String received) {
        return String.format(pattern, expected, received);
    }

    static class JsonCheckerConversionException extends RuntimeException {

        public JsonCheckerConversionException(String failingJsonString) {
            super("Exception while trying to convert '" + failingJsonString + "' to JSON-Object");
        }

        public JsonCheckerConversionException(String failingJsonString, Exception e) {
            super("Exception while trying to convert '" + failingJsonString + "' to JSON-Object", e);
        }
    }

    static class JsonCheckerAssertionError extends AssertionError {

        public JsonCheckerAssertionError(String s) {
            super(s);
        }

        public JsonCheckerAssertionError(String s, Throwable e) {
            super(s, e);
        }
    }

    private class CustomValueMatcher implements ValueMatcher<Object> {

        @Override
        public boolean equal(Object testJson, Object oracleJson) {
            JsonChecker.this.compareJsonStrings(testJson, oracleJson, true);
            return true;
        }
    }

    private class JsonCheckerComparator extends CustomComparator {

        public JsonCheckerComparator() {
            super(JSONCompareMode.LENIENT, new Customization("***", new CustomValueMatcher()));
        }

        @Override
        protected void compareJSONArrayOfJsonObjects(final String key, final JSONArray expected,
            final JSONArray actual, final JSONCompareResult result) throws JSONException {
            if (expected.length() == 1 && actual.length() == 1) {
                compareJSON(expected.getJSONObject(0), actual.getJSONObject(0));
                return;
            }
            super.compareJSONArrayOfJsonObjects(key, expected, actual, result);
        }
    }
}
