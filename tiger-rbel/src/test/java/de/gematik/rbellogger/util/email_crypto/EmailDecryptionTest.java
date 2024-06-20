/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import de.gematik.rbellogger.key.IdentityBackedRbelKey;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import org.bouncycastle.cms.CMSException;
import org.junit.jupiter.api.Test;

class EmailDecryptionTest {

  private final String TEXT = "Text to be signed";
  private final byte[] BYTES = TEXT.getBytes();

  private RbelKeyManager getKeyManager(Path path) {
    RbelKeyManager keyManager = new RbelKeyManager();
    var identity = TigerPkiIdentityLoader.loadRbelPkiIdentityWithGuessedPassword(path.toFile());
    IdentityBackedRbelKey.generateRbelKeyPairForIdentity(identity).forEach(keyManager::addKey);
    return keyManager;
  }

  @Test
  @SneakyThrows
  void encryptAndDecryptWithRsaAndEccRecipient() {
    var message = BYTES;
    var keys1 = getKeyManager(Certs.REC1_P12_PATH);
    var keys2 = getKeyManager(Certs.ECC_P12);

    var encData = Files.readAllBytes(Paths.get("src/test/resources/crypto/encrypted0.encrypted"));

    byte[] decData1 = EmailDecryption.decrypt(encData, keys1).orElse(null);

    byte[] decData2 = EmailDecryption.decrypt(encData, keys2).orElse(null);

    assertArrayEquals(message, decData1);
    assertArrayEquals(message, decData2);
  }

  @Test
  void encryptAndDecprytWithOneRecipientAndECC() throws CMSException, IOException {
    var keys = getKeyManager(Certs.ECC_P12);

    var enc = Files.readAllBytes(Paths.get("src/test/resources/crypto/encrypted1.encrypted"));

    var decryptData = EmailDecryption.decrypt(enc, keys).orElse(null);

    assertArrayEquals(
        BYTES,
        decryptData,
        "The plain byte array and the encrypted and redecrypted byte are not equal.");
  }

  @Test
  void encryptAndDecprytWithOneRecipientAndRSA() throws Exception {
    var signer = getKeyManager(Certs.SIGNER1_P12);

    var enc = Files.readAllBytes(Paths.get("src/test/resources/crypto/encrypted2.encrypted"));

    var decryptData = EmailDecryption.decrypt(enc, signer).orElse(null);

    assertArrayEquals(
        BYTES,
        decryptData,
        "The plain byte array and the encrypted and redecrypted byte are not equal.");
  }
}
