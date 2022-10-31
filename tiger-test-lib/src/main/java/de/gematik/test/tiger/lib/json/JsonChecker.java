/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
        final JSONObject json = convertToJsonObject(jsonStr);
        final JSONObject oracle = convertToJsonObject(oracleStr);

        try {
            for (String oracleKey : oracle.keySet()) {
                if (!keyContainedInSetOrOptional(oracleKey, json.keySet())) {
                    throw new JsonCheckerAssertionError("Expected JSON to have key '" + oracleKey
                        + "', but only found keys '" + json.keySet() + "'");
                }
            }

            if (checkExtraAttributes) {
                // check json keys are all in oracle (either as name or as ____name
                final Optional<JsonCheckerAssertionError> checkerAssertionError = json.keySet().stream()
                    .filter(key -> !keyContainedInSetOrOptional(key, oracle.keySet()))
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
                null, oracle.toString(2), json.toString(2)), nsme);
        }
    }

    private boolean keyContainedInSetOrOptional(String oracleKey, Set<String> keySet) {
        if (oracleKey.startsWith(OPTIONAL_MARKER)) {
            return true;
        }

        return keySet.stream()
            .map(key -> StringUtils.stripStart(key, OPTIONAL_MARKER))
            .anyMatch(oracleKey::equals);
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
            throw new JsonCheckerConversionException(jsonStr);
        }
    }

    private void compareAllAttributes(JSONObject json, JSONObject oracle) {
        final Iterator<String> keyIt = oracle.keys();
        while (keyIt.hasNext()) {
            final String oracleKey = keyIt.next();
            final var oracleValue = oracle.get(oracleKey).toString();
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
            if ((NULL_MARKER.equals(oracleValue) && jsonTarget == JSONObject.NULL)
                || (NULL_MARKER.equals(jsonTarget) && oracleTarget == JSONObject.NULL)) {
                continue;
            }
            if (IGNORE_JSON_VALUE.equals(oracleValue)) {
                continue;
            }
            if (!jsonTarget.getClass().equals(oracleTarget.getClass())) {
                throw new JsonCheckerAssertionError("Expected an '"
                    + oracleTarget.getClass().getSimpleName()
                    + "' at key '" + oracleKey + "', but found '"
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
    }

    final CustomComparator customComparator = new JsonCheckerComparator();

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

    private class CustomValueMatcher implements ValueMatcher<Object> {

        @Override
        public boolean equal(Object oracleJson, Object testJson) {
            try {
                new JSONObject(testJson.toString());
                new JSONObject(oracleJson.toString());
                JsonChecker.this.assertJsonObjectShouldMatchOrContainInAnyOrder(testJson.toString(),
                    oracleJson.toString(), true);
            } catch (final RuntimeException e1) {
                try {
                    new JSONArray(testJson.toString());
                    new JSONArray(oracleJson.toString());
                    JsonChecker.this.assertJsonArrayShouldMatchInAnyOrder(testJson.toString(),
                        oracleJson.toString());
                } catch (final RuntimeException e2) {
                    try {
                        return oracleJson.toString().equals(IGNORE_JSON_VALUE) ||
                            patchString(testJson.toString())
                                .equals(patchString(oracleJson.toString())) ||
                            testJson.toString().matches(oracleJson.toString());
                    } catch (final RuntimeException e3) {
                        e1.printStackTrace();
                        e2.printStackTrace();
                        e3.printStackTrace();
                        throw new JsonCheckerAssertionError(
                            "Unequal JSON-parts: oracle '" + oracleJson + "' and target '" + testJson
                                + "' do not match");
                    }
                }
            }
            return true;
        }
    }

    private static String patchString(String sourceString) {
        return sourceString
            .replace(NULL_MARKER, "null");
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
