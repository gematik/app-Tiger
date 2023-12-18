/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import static java.nio.charset.StandardCharsets.UTF_8;

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.elements.RbelJweEncryptionInfo;
import de.gematik.rbellogger.data.facet.RbelJweFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.JsonUtils;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.lang.JoseException;

@AllArgsConstructor
public class RbelJweWriter implements RbelElementWriter {

  static {
    BrainpoolCurves.init();
  }

  private final RbelKeyManager rbelKeyManager;

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelJweFacet.class);
  }

  @Override
  public byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    final RbelJweFacet rbelJweFacet = oldTargetElement.getFacetOrFail(RbelJweFacet.class);

    return createUpdatedJwe(oldTargetModifiedChild, new String(newContent, UTF_8), rbelJweFacet)
        .getBytes(UTF_8);
  }

  private String createUpdatedJwe(
      RbelElement oldTargetModifiedChild, String newContent, RbelJweFacet rbelJweFacet) {
    final JsonWebEncryption jwe = new JsonWebEncryption();

    ProviderContext context = new ProviderContext();
    context.getGeneralProviderContext().setGeneralProvider("BC");
    jwe.setProviderContext(context);

    writeHeaderInJwe(oldTargetModifiedChild, newContent, rbelJweFacet, jwe);

    jwe.setPayload(extractJweBodyClaims(oldTargetModifiedChild, newContent, rbelJweFacet));

    jwe.setKey(extractJweKey(rbelJweFacet).getKey());

    try {
      return jwe.getCompactSerialization();
    } catch (JoseException e) {
      throw new JweUpdateException("Error writing into Jwe", e);
    }
  }

  private RbelKey extractJweKey(RbelJweFacet rbelJweFacet) {
    return rbelJweFacet
        .getEncryptionInfo()
        .getFacet(RbelJweEncryptionInfo.class)
        .map(RbelJweEncryptionInfo::getDecryptedUsingKeyWithId)
        .flatMap(rbelKeyManager::findKeyByName)
        .flatMap(RbelKey::getMatchingPublicKey)
        .orElseThrow(
            () ->
                new InvalidEncryptionInfo(
                    "Could not find matching public key to \n"
                        + rbelJweFacet.getEncryptionInfo().printTreeStructure()));
  }

  private void writeHeaderInJwe(
      RbelElement oldTargetModifiedChild,
      String newContent,
      RbelJweFacet rbelJweFacet,
      JsonWebEncryption jwe) {
    extractJweHeaderClaim(oldTargetModifiedChild, newContent, rbelJweFacet)
        .forEach(pair -> jwe.setHeader(pair.getKey(), pair.getValue()));
  }

  private List<Map.Entry<String, String>> extractJweHeaderClaim(
      RbelElement oldTargetModifiedChild, String newContent, RbelJweFacet rbelJweFacet) {
    if (rbelJweFacet.getHeader() == oldTargetModifiedChild) {
      return JsonUtils.convertJsonObjectStringToMap(newContent);
    } else {
      return JsonUtils.convertJsonObjectStringToMap(rbelJweFacet.getHeader().getRawStringContent());
    }
  }

  private String extractJweBodyClaims(
      RbelElement oldTargetModifiedChild, String newContent, RbelJweFacet jweFacet) {
    if (jweFacet.getBody() == oldTargetModifiedChild) {
      return newContent;
    } else {
      return jweFacet.getBody().getRawStringContent();
    }
  }

  public class JweUpdateException extends RuntimeException {

    public JweUpdateException(String s, JoseException e) {
      super(s, e);
    }
  }

  public class InvalidEncryptionInfo extends RuntimeException {

    public InvalidEncryptionInfo(String s) {
      super(s);
    }
  }
}
