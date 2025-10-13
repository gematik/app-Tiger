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
package de.gematik.rbellogger.capture;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import javax.crypto.spec.SecretKeySpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
class PcapCaptureTest {

  @Test
  void pcapFile_checkMetadata() {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder().rbelFile("src/test/resources/discDoc.tgr").build();
    final RbelLogger rbelLogger =
        RbelLogger.build(new RbelConfiguration().addCapturer(fileReaderCapturer));

    fileReaderCapturer.initialize();

    assertThat(rbelLogger.getMessageHistory().getFirst())
        .hasStringContentEqualToAtPosition("$.sender.port", "51441")
        .extractChildWithPath("$.sender.domain")
        .asString()
        .matches("(view-|)localhost");
    assertThat(rbelLogger.getMessageHistory().getFirst())
        .hasStringContentEqualToAtPosition("$.receiver.port", "8080")
        .extractChildWithPath("$.receiver.domain")
        .asString()
        .matches("(view-|)localhost");
  }

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(strings = {"deregisterPairing-noPairedUuid.tgr", "deregisterPairing.tgr"})
  void readPcapFile_shouldParseMessages(String fileName) {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder()
            .rbelFile(Path.of("src", "test", "resources", fileName).toString())
            .build();
    final RbelLogger rbelLogger =
        new RbelConfiguration()
            .addKey(
                "IDP symmetricEncryptionKey",
                new SecretKeySpec(
                    DigestUtils.sha256("geheimerSchluesselDerNochGehashtWird"), "AES"),
                RbelKey.PRECEDENCE_KEY_FOLDER)
            .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
            .addCapturer(fileReaderCapturer)
            .constructRbelLogger();

    rbelLogger
        .getValueShader()
        .addJexlNoteCriterion(
            "message.url == '/auth/realms/idp/.well-known/openid-configuration' &&message.method =="
                + " 'GET' && message.request == true && 'RbelHttpRequestFacet' =~ facets",
            "Discovery Document anfragen");
    rbelLogger
        .getValueShader()
        .addJexlNoteCriterion(
            "request.url == '/auth/realms/idp/.well-known/openid-configuration' &&request.method =="
                + " 'GET' && message.response && 'RbelHttpResponseFacet' =~ facets",
            "Discovery Document Response");
    rbelLogger
        .getValueShader()
        .addJexlNoteCriterion(
            "message.url =^ '/sign_response?' " + "&& message.method=='GET' && key == 'scope'",
            "scope Note!!");
    rbelLogger
        .getValueShader()
        .addJexlNoteCriterion("path == 'body.key_verifier.body'", "key verifier body note");
    rbelLogger
        .getValueShader()
        .addJexlNoteCriterion("key == 'code_verifier'", "the long forgotten code verifier");
    rbelLogger.getValueShader().addJexlNoteCriterion("path =$ 'x5c.0'", "some note about x5c");
    rbelLogger
        .getValueShader()
        .addJexlNoteCriterion("key == 'pairing_endpoint'", "Hier gibts die pairings");
    rbelLogger
        .getValueShader()
        .addJexlNoteCriterion("key == 'user_consent'", "Note an einem Objekt");

    fileReaderCapturer.initialize();

    addRandomTimestamps(rbelLogger);

    log.info("start rendering " + LocalDateTime.now());
    final String render = new RbelHtmlRenderer().doRender(rbelLogger.getMessageHistory());
    FileUtils.writeStringToFile(
        new File("target/pairingList.html"), render, Charset.defaultCharset());
    log.info("completed rendering " + LocalDateTime.now());

    assertThat(rbelLogger.getMessageList().get(0).hasFacet(RbelHttpRequestFacet.class)).isTrue();
    assertThat(rbelLogger.getMessageList().get(1).hasFacet(RbelHttpResponseFacet.class)).isTrue();
    assertThat(rbelLogger.getMessageList().get(0).getNotes())
        .extracting("value")
        .containsExactly("Discovery Document anfragen");
    assertThat(rbelLogger.getMessageList().get(1).getNotes())
        .extracting("value")
        .containsExactly("Discovery Document Response");
    assertThat(render)
        .contains("Hier gibts die pairings")
        .contains("some note about x5c")
        .contains("Note an einem Objekt");
  }

  private void addRandomTimestamps(RbelLogger rbelLogger) {
    ZonedDateTime now = ZonedDateTime.now();
    for (RbelElement msg : rbelLogger.getMessageHistory()) {
      msg.addFacet(RbelMessageTimingFacet.builder().transmissionTime(now).build());
      now = now.plusNanos((long) (1000 * 1000 * RandomUtils.nextDouble(10, 1000)));
    }
  }
}
