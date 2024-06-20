/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelDecryptedEmailFacet;
import de.gematik.rbellogger.data.facet.RbelMimeMessageFacet;
import de.gematik.rbellogger.util.email_crypto.EmailDecryption;
import de.gematik.rbellogger.util.email_crypto.RbelDecryptionException;
import de.gematik.rbellogger.util.email_crypto.SignatureVerification;
import java.io.IOException;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.james.mime4j.dom.SingleBody;
import org.bouncycastle.cms.CMSException;

public class RbelEncryptedMailConverter implements RbelConverterPlugin {

  private static class EmailDecryptionFailedException extends Exception {}

  @Override
  @SneakyThrows
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    if (Optional.ofNullable(rbelElement.getParentNode())
        .filter(node -> node.hasFacet(RbelMimeMessageFacet.class))
        .isPresent()) {
      try {
        RbelDecryptedEmailFacet facet = parseEncryptedMessage(rbelElement, converter);
        rbelElement.addFacet(facet);
        converter.convertElement(facet.getDecrypted());
      } catch (CMSException | EmailDecryptionFailedException e) {
        // ignore
      }
    }
  }

  private RbelDecryptedEmailFacet parseEncryptedMessage(RbelElement body, RbelConverter context)
      throws CMSException, IOException, EmailDecryptionFailedException {
    var keyManager = context.getRbelKeyManager();

    final byte[] decryptedMessage =
        EmailDecryption.decrypt(body.getRawContent(), keyManager)
            .orElseThrow(EmailDecryptionFailedException::new);

    final byte[] signedMessageContent = extractContentFromMessage(decryptedMessage);

    final byte[] rfc822Message = extractRfc822Message(signedMessageContent);

    RbelElement decrypted = new RbelElement(rfc822Message, body);
    return RbelDecryptedEmailFacet.builder().decrypted(decrypted).build();
  }

  private static byte[] extractRfc822Message(byte[] signedMessageContent) throws IOException {
    return SignatureVerification.validate(signedMessageContent).getOriginalData();
  }

  private static byte[] extractContentFromMessage(final byte[] data) throws IOException {
    var message = RbelMimeConverter.parseMimeMessage(data);
    var body = message.getBody();
    if (body instanceof SingleBody singleBody) {
      return singleBody.getInputStream().readAllBytes();
    }
    throw new RbelDecryptionException("multipart body handling not implemented"); // NOSONAR
  }
}
