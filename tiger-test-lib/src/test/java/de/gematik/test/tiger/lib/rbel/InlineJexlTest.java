/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.addSomeMessagesToTigerTestHooks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.TigerParameterTypeDefinitions;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.enums.ModeType;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import io.restassured.http.Method;
import java.net.URI;
import lombok.SneakyThrows;
import org.apache.commons.jexl3.JexlException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

public class InlineJexlTest {

  {
    RbelMessageValidator.instance.getRbelMessages();
  }

  @BeforeAll
  public static void addSomeMessages() {
    TigerGlobalConfiguration.reset();
    ReflectionTestUtils.setField(TigerDirector.class, "initialized", true);
    final TigerTestEnvMgr mock = mock(TigerTestEnvMgr.class);
    ReflectionTestUtils.setField(TigerDirector.class, "tigerTestEnvMgr", mock);
    addSomeMessagesToTigerTestHooks();
  }

  @AfterAll
  public static void cleanUp() {
    ReflectionTestUtils.setField(TigerDirector.class, "initialized", false);
    ReflectionTestUtils.setField(TigerDirector.class, "tigerTestEnvMgr", null);
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
  void placeholdersStartingWithParenthesesRebelContentType(String resolve, String shouldMatch) {
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
