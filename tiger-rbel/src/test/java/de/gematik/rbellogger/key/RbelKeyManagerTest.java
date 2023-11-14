/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.key;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelConverter;
import java.io.IOException;
import java.security.Key;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelKeyManagerTest {

  private final RbelKeyManager keyManager = new RbelKeyManager();
  private final Key mock = mock(Key.class);

  @BeforeEach
  public void initEach() {
    doReturn(new byte[] {}).when(mock).getEncoded();
  }

  @Test
  void shouldFindPrivateKeyIfPresent() {
    RbelKey publicKey = RbelKey.builder().keyName("publicKey").key(mock).build();
    keyManager.addKey(publicKey);
    RbelKey falsePrivateKey =
        RbelKey.builder()
            .matchingPublicKey(RbelKey.builder().keyName("other publicKey").key(mock).build())
            .keyName("falsePrivateKey")
            .key(mock)
            .build();
    keyManager.addKey(falsePrivateKey);

    RbelKey privateKey =
        RbelKey.builder().matchingPublicKey(publicKey).keyName("privateKey").key(mock).build();
    keyManager.addKey(privateKey);

    assertThat(keyManager.findCorrespondingPrivateKey(publicKey.getKeyName()))
        .get()
        .isEqualTo(privateKey);
  }

  @Test
  void shouldThrowExceptionWhenPrivateKeyNotPresent() {
    keyManager.getAllKeys().collect(Collectors.toList()).clear();

    RbelKey publicKey = RbelKey.builder().keyName("publicKey").key(mock).build();
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
        null,
        null,
        Optional.empty());

    assertThat(converter.getRbelKeyManager().findKeyByName("puk_fed_sig")).isPresent();
  }
}
