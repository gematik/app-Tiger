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
package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.TigerParameterTypeDefinitions;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import io.restassured.http.Method;
import java.net.URI;
import lombok.SneakyThrows;
import org.apache.commons.jexl3.JexlException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class InlineJexlTest {

  private static LocalProxyRbelMessageListenerTestAdapter localProxyRbelMessageListenerTestAdapter;

  @BeforeAll
  public static void addSomeMessages() {
    TigerGlobalConfiguration.reset();
    localProxyRbelMessageListenerTestAdapter = new LocalProxyRbelMessageListenerTestAdapter();
    // Calling the constructor will register the JexlToolbox
    new RbelMessageRetriever(
        mock(TigerTestEnvMgr.class),
        mock(TigerProxy.class),
        localProxyRbelMessageListenerTestAdapter.getLocalProxyRbelMessageListener());
  }

  @AfterAll
  public static void resetTiger() {
    TigerGlobalConfiguration.reset();
  }

  @BeforeEach
  public void reset() {
    localProxyRbelMessageListenerTestAdapter.addSomeMessagesToTigerTestHooks();
  }

  @ParameterizedTest
  @CsvSource({
    "!{rbel:lastResponseAsString()}, HTTP\\/1\\.1 200\\X*",
    "!{rbel:lastRequestAsString()}, POST \\/token HTTP\\/1.1\\X*",
    "'!{rbel:getValueAtLocationAsString(rbel:lastResponse(), \"$..id_token..wasDecryptable\")}',"
        + " false",
    "'!{rbel:getValueAtLocationAsString(rbel:lastRequest(), \"$..key_verifier..alg.content\")}',"
        + " ECDH-ES\\+A256KW"
  })
  void resolveTestStrings(String resolve, String shouldMatch) {
    assertThat(TigerGlobalConfiguration.resolvePlaceholders(resolve)).matches(shouldMatch);
  }

  @ParameterizedTest
  @CsvSource({
    "'!{rbel:lastResponse11111111AsString()}'",
    "'!{rbel:lastResponse().isResponse}'",
    "'!{rbel:lastResponse(),,,isResponse}'",
  })
  void resolveTestStringsFailures(String resolve) {
    assertThatThrownBy(() -> TigerGlobalConfiguration.resolvePlaceholders(resolve))
        .isInstanceOfAny(JexlException.class, RuntimeException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "'!{rbel:lastResponse(),isResponse'",
    "'!{rbel:getValueAtLocationAsString(rbel:lastResponse(), \"$.#%.id_token..wasDecryptable\")}'",
    "'!{rbel:getValueAtLocationAsString(rbel:lastResponse(), \"$..id_token[1]..wasDecryptable\")}'",
  })
  void resolveTestStringsFailuresSilently(String resolve) {
    assertThat(TigerGlobalConfiguration.resolvePlaceholders(resolve)).isEqualTo(resolve);
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({
    "\"GET\", GET",
    "GET, GET",
    "\"PUT\", PUT",
    "PUT, PUT",
    "\"POST\", POST",
    "POST, POST"
  })
  void placeholdersRequestTypeResolveHttpRequestType(String resolve, String shouldMatch) {
    assertThat(TigerParameterTypeDefinitions.requestType(resolve))
        .isEqualTo(Method.valueOf(shouldMatch));
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({"\"JSON\", JSON", "JSON, JSON", "\"XML\", XML", "XML, XML"})
  void placeholdersModeTypeCorrectlyResolveToModeType(String resolve, String shouldMatch) {
    assertThat(TigerParameterTypeDefinitions.modeType(resolve))
        .isEqualTo(ModeType.valueOf(shouldMatch));
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({
    "\"XML\", XML",
    "XML, XML",
    "\"JSON\", JSON",
    "JSON, JSON",
    "\"JWT\", JWT",
    "JWT, JWT"
  })
  void placeholdersStartingWithParenthesesRbelContentType(String resolve, String shouldMatch) {
    assertThat(TigerParameterTypeDefinitions.rbelContentType(resolve))
        .isEqualTo(RbelContentType.valueOf(shouldMatch));
  }

  @SneakyThrows
  @Test
  void placeholdersResolvedUrlResolveCorrectlyURL() {
    assertThat(TigerParameterTypeDefinitions.tigerResolvedUrl("http://someUrl"))
        .isEqualTo(new URI("http://someUrl"));
  }
}
