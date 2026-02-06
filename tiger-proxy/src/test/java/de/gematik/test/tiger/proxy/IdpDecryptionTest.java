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
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LENIENT_HTTP_PARSING;

import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@ResetTigerConfiguration
class IdpDecryptionTest extends AbstractTigerProxyTest {

  @Test
  void shouldAddRecordIdFacetToAllHandshakeMessages() throws Exception {
    LENIENT_HTTP_PARSING.putValue(true);
    try {
      spawnTigerProxyWith(
          TigerProxyConfiguration.builder()
              .keyFolders(List.of("src/test/resources"))
              .activateRbelParsingFor(List.of("epa-vau"))
              .build());

      final RbelFileReaderCapturer fileReaderCapturer =
          RbelFileReaderCapturer.builder()
              .rbelFile("src/test/resources/idpDecryption.tgr")
              .rbelConverter(tigerProxy.getRbelLogger().getRbelConverter())
              .build();
      fileReaderCapturer.initialize();
      fileReaderCapturer.close();

      awaitMessagesInTigerProxy(22);

      final String htmlData = RbelHtmlRenderer.render(tigerProxy.getRbelLogger().getMessages());
      FileUtils.writeStringToFile(
          new File("target/idpFlow.html"), htmlData, StandardCharsets.UTF_8);

      assertThat(tigerProxy.getRbelMessagesList().get(21))
          .hasGivenValueAtPosition(
              "$.body.access_token.content.encryptionInfo.decryptedUsingKeyWithId", "token_key");
    } finally {
      LENIENT_HTTP_PARSING.clearValue();
    }
  }
}
