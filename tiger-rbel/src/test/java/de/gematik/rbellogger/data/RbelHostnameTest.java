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

package de.gematik.rbellogger.data;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class RbelHostnameTest {

    @ParameterizedTest
    @CsvSource(value = {
        "'http://foo',                  foo,                80",
        "'https://foo',                 foo,                443",
        "'https://www.google.de:666',   'www.google.de',    666",
        "'https://foo:666',             'foo',              666",
        "'https://not.real.server',     'not.real.server',  443",
        "'frummel://not.real.server',   'not.real.server',  0"
    })
    public void generateFromUrlAndAssertResult(String rawUrl, String expectedHostname, int expectedPort) {
        assertThat(RbelHostname.generateFromUrl(rawUrl))
            .get()
            .hasFieldOrPropertyWithValue("hostname", expectedHostname)
            .hasFieldOrPropertyWithValue("port", expectedPort);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "''",
        "foo:666",
        "https://foo__:666",
        "https://foo:-1"
    })
    public void generateFromUrlAndExpectEmptyResult(String rawUrl) {
        assertThat(RbelHostname.generateFromUrl(rawUrl))
            .isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {
        "'test:80',       'test',         80",
        "'test',          'test',         0",
        "'127.0.0.1:543', '127.0.0.1',    543",
        "'127.0.0.1',     '127.0.0.1',    0",
        "'test:-1',       test,           -1",
        "'test__',        test__,         0",
        "'test__:111',    test__,         111"
    })
    public void generateFromStringAndAssertResult(String rawUrl, String expectedHostname, int expectedPort) {
        assertThat(RbelHostname.fromString(rawUrl)).get()
            .hasFieldOrPropertyWithValue("hostname", expectedHostname)
            .hasFieldOrPropertyWithValue("port", expectedPort);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "''"
    })
    public void generateFromStringAndExpectEmptyResult(String rawUrl) {
        assertThat(RbelHostname.fromString(rawUrl))
            .isEmpty();
    }
}
