/*
 * Copyright (c) 2023 gematik GmbH
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.lib.json.JsonChecker.JsonCheckerConversionException;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
@Slf4j
class JsonCheckerTest {

    private static final String IDP_STRUCT = "{\n"
        + "  iss: 'http.*',\n"
        + "  sub: 'http.*',\n"
        + "  iat: \"${json-unit.ignore}\",\n"
        + "  exp: \"${json-unit.ignore}\",\n"
        + "  jwks: {\n"
        + "    keys: [\n"
        + "      {\n"
        + "        use: \"sig\",\n"
        + "        kid: \"puk_fachdienst_sig\",\n"
        + "        kty: \"EC\",\n"
        + "        crv: \"P-256\",\n"
        + "        x: \"${json-unit.ignore}\",\n"
        + "        y: \"${json-unit.ignore}\"\n"
        + "      }\n"
        + "    ]\n"
        + "  },\n"
        + "  authority_hints: [\"todo Bezeichnung des Federation Master\"]\n"
        + "}\n";
    final JsonChecker check = new JsonChecker();

    private Stream<Arguments> provideDataForMatchingJsonObjects() {
        return Stream.of(
            Arguments.of("Match json: extra field, optional field",
                "{ attr1: 'val1' }",
                "{ attr1: 'val1', ____attr2: 'val2' } ", Optional.empty(), true),

            Arguments.of("Match json: optional field",
                "{ attr1:'val1', attr2:'val2' }",
                "{ attr1: 'val1', ____attr2: 'val2' }", Optional.empty(), true),

            Arguments.of("Match json: optional field with regex value",
                "{ attr1:'val1', attr2:'val2' }",
                "{ attr1: 'val1', ____attr2: 'v.*' }", Optional.empty(), true),

            Arguments.of("Match json: optional field with incorrect regex value",
                "{ attr1:'val1', attr2:'val2' }",
                "{ attr1: 'val1', ____attr2: 'valXXX' }", Optional.of(AssertionError.class), true),

            Arguments.of("Match json: optional field with incorrect regex value",
                "{ attr1:'val1', attr2:'val2' }",
                "{ attr1: 'val1', ____attr2: 'v?\\\\d' }", Optional.of(AssertionError.class), true),

            Arguments.of("Match json: missing field, optional field, extra attributes checked",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val1', ____attr2: 'val2' }", Optional.of(AssertionError.class), true),

            Arguments.of("Match json: missing field, optional field, extra attributes not checked",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val1', ____attr2: 'val2' }", Optional.empty(), false),

            Arguments.of("Match json: missing fields, extra attributes not checked",
                "{ attr1: 'val1', attr3: 'val3', attr4:'val4' }",
                "{ attr1: 'val1'}", Optional.empty(), false),

            Arguments.of("Match json: invalid oracle string",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val1', ____attr2: 'val2' ::::  }", Optional.of(JsonCheckerConversionException.class), false),

            Arguments.of("Match json: invalid json",
                "{ attr1: 'val1', attr3: 'val3' :::: }",
                "{ attr1: 'val1', ____attr2: 'val2'  }", Optional.of(JsonCheckerConversionException.class), false),

            Arguments.of("Match json: null value is equal to $NULL",
                "{ attr1: 'val1', attr3: null }",
                "{ attr1: 'val1', attr3: '$NULL'}", Optional.empty(), true),

            Arguments.of("Match json: nested object, valid",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }", Optional.empty(), true),

            Arguments.of("Match json: nested object, correct regex",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { sub1: 'v.*1' }, attr2: 'val2' }", Optional.empty(), true),

            Arguments.of("Match json: nested value equal to $NULL",
                "{ attr1: { sub1: null }, attr2:'val2' }",
                "{ attr1: { sub1: '$NULL' }, attr2: 'val2' }", Optional.empty(), true),

            Arguments.of("Match json: nested object, incorrect regex (1)",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { sub1: 'val1xxxx' }, attr2: 'val2' }", Optional.of(AssertionError.class), true),

            Arguments.of("Match json: nested object, incorrect regex (2)",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { sub1: 'xxx.*' }, attr2: 'val2' }", Optional.of(AssertionError.class), true),

            Arguments.of("Match json: optional field in nested object",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { ____sub1: 'val1' }, attr2:'val2' }", Optional.empty(), true),

            Arguments.of("Match json: extra field in nested object, extra attributes not checked",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { sub1: 'val1', ____sub2: 'val2' }, attr2:'val2' }", Optional.empty(), false),

            Arguments.of("Match json: extra field in nested object, extra attributes checked",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { sub1: 'val1', ____sub2: 'val2' }, attr2:'val2' }", Optional.empty(), true),

            Arguments.of("Match json: optional field in a nested array",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',____friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: optional nested field in a nested array",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',____pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: extra nested field in nested object, extra attributes checked",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',____surname:'Smith',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: extra field in nested object, extra attributes not checked",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',____surname:'Smith',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                Optional.empty(), false),

            Arguments.of("Match json: ignore value of the key",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val1', attr3: \"${json-unit.ignore}\"} }", Optional.empty(), true),

            Arguments.of("Match json: equals tested before regex",
                "{ attr1: '\\\\d+', attr3: 'val3' }",
                "{ attr1: '\\\\d+', attr3: 'val3' }", Optional.empty(), true),

            Arguments.of("Match json: ignore value of the nested key",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "{ attr1: { sub1: \"${json-unit.ignore}\" }, attr2:'val2' }", Optional.empty(), true),

            Arguments.of("Match json: missing fields in a nested array, extra attributes not checked",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe'}",
                Optional.empty(), false),

            Arguments.of("Match json: missing field in a nested array, extra attributes checked",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',pets:[]}",
                Optional.of(AssertionError.class), true),

            Arguments.of("Match json: ignore value of a bigger nested key",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:\"${json-unit.ignore}\" ,pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: nested array",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: nested array, lenient mode",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:[{pets:['dog'],id:2,name:'Pat'},{pets:['cat','fish'],id:3,name:'Sue'}],pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: nested array, $NULL value",
                "{id:1,name:'Joe',friends:null,pets:[]}",
                "{id:1,name:'Joe',friends:'$NULL',pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: nested value in nested array, $NULL value",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:null},{id:3,name:null,pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:$NULL},{id:3,name:$NULL,pets:['cat','fish']}],pets:[]}",
                Optional.empty(), true),

            Arguments.of("Match json: missing attributes within nested field, extra attributes not checked",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:null},{id:3,name:null,pets:['cat','fish']}],pets:[]}",
                "{id:1,name:'Joe',friends:[{pets:$NULL},{name:$NULL,pets:['cat','fish']}],pets:[]}",
                Optional.empty(), false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideDataForMatchingJsonObjects")
    void testMatchOContainInAnyOrderJsonObjects(String testInfo, String jsonStr, String oracleStr,
        Optional<Class<Error>> exception, boolean checkExtraAttributes) {
        log.info(testInfo);
        if (exception.isEmpty()) {
            check.assertJsonObjectShouldMatchOrContainInAnyOrder(
                jsonStr,
                oracleStr, checkExtraAttributes);
        } else {
            assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
                jsonStr,
                oracleStr, checkExtraAttributes))
                .isInstanceOf(exception.get());
        }
    }

    private Stream<Arguments> provideDataForMatchingJsonAttributes() {
        return Stream.of(
            Arguments.of("Match value to key: absolute match",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "val3", Optional.empty()),

            Arguments.of("Match value to key: null value",
                "{ attr1: 'val1', attr3: null }",
                "attr3", null, Optional.empty()),

            Arguments.of("Match value to key: null value, $REMOVE",
                "{ attr1: 'val1', attr3: null }",
                "attr3", "$REMOVE", Optional.of(AssertionError.class)),

            Arguments.of("Match value to key: no null value",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", null, Optional.of(AssertionError.class)),

            Arguments.of("Match value to key: using $REMOVE",
                "{ attr1: 'val1', attr5: 'val3' }",
                "attr3", "$REMOVE", Optional.empty()),

            Arguments.of("Match value to key: using $REMOVE, key exists",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "$REMOVE", Optional.of(AssertionError.class)),

            Arguments.of("Match value to key: with correct regex",
                "{ attr1: 'val1', attr3: 'val3' }", "attr3",
                "v.*", Optional.empty()),

            Arguments.of("Match value to key: with incorrect regex (1)",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "val3XXXX", Optional.of(AssertionError.class)),

            Arguments.of("Match value to key: with incorrect regex (2)",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "xxx.*", Optional.of(AssertionError.class)),

            //TODO: "TGR-337: JsonChecker unterstützt nested Structures"

//            Arguments.of("Match value to key: nested object, correct value",
//                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
//                "sub1", "val1", Optional.empty()),

            Arguments.of("Match value to key: nested object, check nested key",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "attr1", "{\"sub1\":\"val1\"}", Optional.empty()),

            Arguments.of("Match value to key: nested object, $REMOVE, key exists",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "attr1", "$REMOVE", Optional.of(AssertionError.class)),

//            Arguments.of("Match value to key: nested object,regex for nested value",
//                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
//                "attr1", "(?>=\"attr1\"\\ *:\\)(?:\\})*", Optional.empty()),

            Arguments.of("Match value to key: nested object, $REMOVE, key exists",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "attr1", "$REMOVE", Optional.of(AssertionError.class)),

            Arguments.of("Match value to key: nested object, $REMOVE, no key exists",
                "{ attr2: { sub1: 'val1' }, attr5:'val2' }",
                "attr1", "$REMOVE", Optional.empty()),

//            Arguments.of("Match value to key: nested object, correct regex",
//                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
//                "sub1", "v.*", Optional.empty()),
//
//            Arguments.of("Match value to key: nested object, incorrect regex",
//                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
//                "sub1", "v.*", Optional.empty()),

            Arguments.of("Match value to key: nested object, regex for second value correct",
                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
                "attr2", "v.*", Optional.empty()),

            Arguments.of("Match value to key: nested array, get value",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "friends",
                "[{\"pets\":[\"dog\"],\"name\":\"Pat\",\"id\":2},{\"pets\":[\"cat\",\"fish\"],\"name\":\"Sue\",\"id\":3}]",
                Optional.empty()),

//            Arguments.of("Match value to key: nested array, get nested value",
//                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
//                "friends[pets]","dog", Optional.empty()),

            Arguments.of("Match value to key: nested array, $REMOVE, key exists",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "friends", "$REMOVE", Optional.of(AssertionError.class)),

            Arguments.of("Match value to key: extra field, $REMOVE, no key exists",
                "{id:1,name:'Joe',friends:[{id:2,name:'Pat',pets:['dog']},{id:3,name:'Sue',pets:['cat','fish']}],pets:[]}",
                "names", "$REMOVE", Optional.empty())
        );
    }

    @ParameterizedTest
    @MethodSource("provideDataForMatchingJsonAttributes")
    void testJsonAttributeShouldMatch(String testInfo, String jsonObject, String claimName, String regex,
        Optional<Class<Error>> exception) {
        log.info(testInfo);
        if (exception.isEmpty()) {
            check.assertJsonAttributeShouldMatch(
                new JSONObject(jsonObject),
                claimName, regex);
        } else {
            assertThatThrownBy(() -> check.assertJsonAttributeShouldMatch(
                new JSONObject(jsonObject),
                claimName, regex))
                .isInstanceOf(exception.get());
        }
    }

    private static Stream<Arguments> provideDataForNotMatchingJsonAttributes() {
        return Stream.of(
            Arguments.of("Not match value to key: key matches value",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "val3", Optional.of(AssertionError.class)),

            Arguments.of("Not match value to key: key matches null",
                "{ attr1: 'val1', attr3: null }",
                "attr3", null, Optional.of(AssertionError.class)),

            Arguments.of("Not match value to key: key not matches Optional.empty()",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", null, Optional.empty()),

            Arguments.of("Not match value to key: $REMOVE, no key present",
                "{ attr1: 'val1', attr5: 'val3' }",
                "attr3", "$REMOVE", Optional.of(AssertionError.class)),

            Arguments.of("Not match value to key: $REMOVE, key present",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "$REMOVE", Optional.empty()),

            Arguments.of("Not match value to key: regex for value incorrect",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "val3XXXX", Optional.empty()),

            Arguments.of("Not match value to key: regex for value correct",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "v.*", Optional.of(AssertionError.class)),

            Arguments.of("Not match value to key: regex for value incorrect",
                "{ attr1: 'val1', attr3: 'val3' }",
                "attr3", "xxx.*", Optional.empty())

            //TODO: "TGR-337: JsonChecker unterstützt nested Structures"

//            Arguments.of("Not match value to key: nested object, incorrect value",
//                "{ attr1: { sub1: 'val1' }, attr2:'val2' }",
//                "sub1", Optional.empty(), Optional.empty()),
//
//            Arguments.of("Match value to key: $REMOVE as value, exception",
//                "{ attr1: '$REMOVE', attr3: 'val3' }",
//                "attr1", "Optional.empty()", Optional.of(AssertionError.class)),
//
//            Arguments.of("Match value to key: $REMOVE as value, no exception",
//                "{ attr1: '$REMOVE', attr3: 'val3' }",
//                "attr1", "$REMOVE", Optional.empty())
        );
    }

    @ParameterizedTest
    @MethodSource("provideDataForNotMatchingJsonAttributes")
    void testJsonAttributeShouldNotMatch(String testInfo, String jsonObject, String claimName, String
        regex,
        Optional<Class<Error>> exception) {
        log.info(testInfo);
        if (exception.isEmpty()) {
            check.assertJsonAttributeShouldNotMatch(
                new JSONObject(jsonObject),
                claimName, regex);
        } else {
            assertThatThrownBy(() -> check.assertJsonAttributeShouldNotMatch(
                new JSONObject(jsonObject),
                claimName, regex))
                .isInstanceOf(exception.get());
        }
    }

    private static Stream<Arguments> provideDataForCompareJsonStrings() {
        return Stream.of(
            Arguments.of("Compare two simple objects (success)",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val1', attr3: 'val3' }",
                false, Optional.empty()),

            Arguments.of("Compare two simple objects (fail)",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val2', attr3: 'val3' }",
                false, Optional.of("Comparison failed at key 'attr1'")),

            Arguments.of("Compare two simple arrays (success)",
                "[0,1,2,3345]",
                "[0,1,2,3345]",
                false, Optional.empty()),

            Arguments.of("Compare two simple arrays (fail)",
                "[0,1,2,3345]",
                "[0,1,2,3]",
                false, Optional.of("Unexpected: 3345")),

            Arguments.of("Compare two different types (1)",
                "{'foo':'bar'}",
                "[0,1,2,3]",
                false, Optional.of("Could not compare JSONObject to JSONArray: Different types!")),

            Arguments.of("Compare two different types (2)",
                "[0,1,2,3]",
                "{'foo':'bar'}",
                false, Optional.of("Could not compare JSONArray to JSONObject: Different types!")),

            Arguments.of("Compare two different types (2)",
                "{'foo':'bar'}",
                "hallo du",
                false, Optional.of("Could not compare JSONObject to String: Different types!")),

            Arguments.of("Compare two objects with extra attribute (pass)",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val1' }",
                false, Optional.empty()),

            Arguments.of("Compare object with extra attribute (fail)",
                "{ attr1: 'val1', attr3: 'val3' }",
                "{ attr1: 'val1' }",
                true, Optional.of("EXTRA Key attr3 detected in received in JSON")),

            Arguments.of("Compare object in array with extra attribute (pass)",
                "[{ attr1: 'val1', attr3: 'val3' }]",
                "[{ attr1: 'val1' }]",
                false, Optional.empty()),

            Arguments.of("Compare object in array with extra attribute (fail)",
                "[{ attr1: 'val1', attr3: 'val3' }]",
                "[{ attr1: 'val1' }]",
                true, Optional.of("EXTRA Key attr3 detected in received in JSON")),

            Arguments.of("Compare two arrays with different containing types (1)",
                "[{'foo':'bar'}]",
                "['notAnObject']",
                false, Optional.of("Unexpected: a JSON object")),

            Arguments.of("Compare two objects in array with extra attribute (pass)",
                "[{ attr1: 'val1'}, { attr2: 'val1'}]",
                "[{ attr1: 'val1'}, { attr2: 'val1'}]",
                false, Optional.empty()),

            Arguments.of("Compare two objects in array with extra attribute (fail)",
                "[{ attr1: 'val1'}, { attr2: 'val1'}]",
                "[{ attr1: 'val1'}, { attr2: 'val2'}]",
                false, Optional.of("Could not find match for element {\"attr2\":\"val2\"}")),

            Arguments.of("Compare array in array",
                "[[1,2],[foo, bar]]",
                "[[1,2],[foo, bar]]",
                false, Optional.empty()),

            Arguments.of("Compare object in array with extra attribute (fail)",
                "[{ attr1: 'val1' }]",
                "[{ attr1: 'val1', attr3: 'val3' }]",
                false, Optional.of("Expected JSON to have key 'attr3', but only found keys '[attr1]'"))
            );
    }

    @ParameterizedTest
    @MethodSource("provideDataForCompareJsonStrings")
    void compareJsonStrings(String testInfo, String jsonStr, String oracleStr, Boolean checkExtraAttributes,
        Optional<String> exception) {

        log.info(testInfo);
        if (exception.isEmpty()) {
            check.compareJsonStrings(jsonStr, oracleStr, checkExtraAttributes);
        } else {
            assertThatThrownBy(() -> check.compareJsonStrings(
                jsonStr,
                oracleStr, checkExtraAttributes))
                .hasMessageContaining(exception.get());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"N_o_T a {{{ j[[son; {\"correct\":\"json\"}; N_o_T",
        "{\"correct\":\"json\"}; N_o_T a {{{ j[[son; N_o_T",
        "{\"array\":\"json\"}; {\"array\":[1,2,3]}; Comparison failed at key 'array'",
        "{\"array\":[1,2,3]}; {\"array\":\"json\"}; Comparison failed at key 'array'"
    },
        delimiter = ';')
    void testMatchOContainInAnyOrderJsonObjects(String jsonStr, String oracleStr,
        String exceptionShouldContain) {
        assertThatThrownBy(() -> check.assertJsonObjectShouldMatchOrContainInAnyOrder(
            jsonStr,
            oracleStr, true))
            .hasMessageContaining(exceptionShouldContain);
    }

    @Test
    void idpJson() {
        check.compareJsonStrings(IDP_STRUCT, IDP_STRUCT, false);
    }

    @Test
    void nestedFunctionalAttributeValues() {
        check.compareJsonStrings("{jwks: {keys: [{y: \"some-value\"}]}}", "{jwks: {keys: [{y: \"${json-unit.ignore}\"}]}}", false);
        check.compareJsonStrings("{'blub':[{'foo':'bar'}]}", "{'blub':[{'foo': '${json-unit.ignore}'}]}", false);
    }
}
