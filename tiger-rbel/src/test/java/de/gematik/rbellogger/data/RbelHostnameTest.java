/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.data;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.util.RbelSocketAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelHostnameTest {

  private static final String IPV4_REGEX = "\\d{1,3}.\\d{1,3}.\\d{1,3}.\\d{1,3}";

  @ParameterizedTest
  @CsvSource(
      value = {
        "'http://foo',                foo,                80",
        "'https://foo',               foo,                443",
        "'https://example.com:666',   'example.com',      666",
        "'https://foo:666',           'foo',              666",
        "'https://not.real.server',   'not.real.server',  443",
        "'frummel://not.real.server', 'not.real.server',  0"
      })
  void generateFromUrlAndAssertResult(String rawUrl, String expectedHostname, int expectedPort) {
    assertThat(RbelSocketAddress.generateFromUrl(rawUrl))
        .get()
        .hasFieldOrPropertyWithValue("port", expectedPort)
        .extracting(RbelSocketAddress::printHostname)
        .matches(hostname -> hostname.matches(expectedHostname), expectedHostname);
  }

  @ParameterizedTest
  @CsvSource(value = {"''", "foo:666", "https://foo__:666", "https://foo:-1"})
  void generateFromUrlAndExpectEmptyResult(String rawUrl) {
    assertThat(RbelSocketAddress.generateFromUrl(rawUrl)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "'lufxrqwe:80',        'lufxrqwe',              80",
        "'lufxrqwe',           'lufxrqwe',              0",
        "'127.0.0.1:543',      '(view-|)localhost',     543",
        "'127.0.0.1',          '(view-|)localhost',     0",
        "'1.2.3.4',            '1.2.3.4',               0",
        "'lufxrqwe:-1',        'lufxrqwe',              -1",
        "'lufxrqwe__',         'lufxrqwe__',            0",
        "'lufxrqwe__:111',     'lufxrqwe__',            111",
        "'[2001:db8::1]:8080', '2001:db8:0:0:0:0:0:1',  8080",
        "'[dc45::1]',          'dc45:0:0:0:0:0:0:1',    0"
      })
  void generateFromStringAndAssertResult(String rawUrl, String expectedHostname, int expectedPort) {
    assertThat(RbelSocketAddress.fromString(rawUrl))
        .get()
        .hasFieldOrPropertyWithValue("port", expectedPort)
        .extracting(RbelSocketAddress::printHostname)
        .matches(hostname -> hostname.matches(expectedHostname), expectedHostname);
  }

  @ParameterizedTest
  @CsvSource(value = {"''"})
  void generateFromStringAndExpectEmptyResult(String rawUrl) {
    assertThat(RbelSocketAddress.fromString(rawUrl)).isEmpty();
  }
}
