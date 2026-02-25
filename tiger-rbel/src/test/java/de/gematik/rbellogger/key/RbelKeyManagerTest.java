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
package de.gematik.rbellogger.key;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Key;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RbelKeyManagerTest {

  private final RbelKeyManager keyManager = new RbelKeyManager();

  private Key mockKey() {
    final byte[] bytes = new byte[32];
    ThreadLocalRandom.current().nextBytes(bytes);
    var mock = mock(Key.class);
    doReturn(bytes).when(mock).getEncoded();
    return mock;
  }

  @Test
  void shouldFindPrivateKeyIfPresent() {
    RbelKey publicKey = RbelKey.builder().keyName("publicKey").key(mockKey()).build();
    keyManager.addKey(publicKey);
    RbelKey falsePrivateKey =
        RbelKey.builder()
            .matchingPublicKey(RbelKey.builder().keyName("other publicKey").key(mockKey()).build())
            .keyName("falsePrivateKey")
            .key(mockKey())
            .build();
    keyManager.addKey(falsePrivateKey);

    RbelKey privateKey =
        RbelKey.builder().matchingPublicKey(publicKey).keyName("privateKey").key(mockKey()).build();
    keyManager.addKey(privateKey);

    assertThat(keyManager.findCorrespondingPrivateKey(publicKey.getKeyName()))
        .get()
        .isEqualTo(privateKey);
  }

  @Test
  void shouldThrowExceptionWhenPrivateKeyNotPresent() {
    keyManager.getAllKeys().collect(Collectors.toList()).clear();

    RbelKey publicKey = RbelKey.builder().keyName("publicKey").key(mockKey()).build();
    keyManager.addKey(publicKey);

    assertThat(keyManager.findCorrespondingPrivateKey(publicKey.getKeyName())).isEmpty();
  }

  @Test
  void shouldGrepJwkKeys() throws IOException {
    RbelConverter converter = RbelLogger.build().getRbelConverter();

    converter.parseMessage(
        readCurlFromFileWithCorrectedLineBreaks(
                "src/test/resources/sampleMessages/jwtWithKeysClaim.curl")
            .getBytes(),
        new RbelMessageMetadata());

    assertThat(converter.getRbelKeyManager().findKeyByName("puk_fed_sig")).isPresent();
  }

  @Test
  void shouldBeAbleToHandle() throws IOException {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            Path.of("../tiger-common/src/test/resources/testTigerYaml/tiger.yaml").toString()));
    RbelConverter converter =
        RbelLogger.build(
                new RbelConfiguration()
                    .addInitializer(
                        new RbelKeyFolderInitializer(
                            Path.of("src/test/resources").toAbsolutePath().toString())))
            .getRbelConverter();

    converter.parseMessage(
        readCurlFromFileWithCorrectedLineBreaks(
                "src/test/resources/sampleMessages/jwtWithKeysClaim.curl")
            .getBytes(),
        new RbelMessageMetadata());

    assertThat(converter.getRbelKeyManager().findKeyByName("puk_fed_sig")).isPresent();
  }
}
