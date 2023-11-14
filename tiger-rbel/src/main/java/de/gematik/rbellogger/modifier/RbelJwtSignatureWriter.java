/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import static java.nio.charset.StandardCharsets.UTF_8;

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.elements.RbelJwtSignature;
import lombok.AllArgsConstructor;
import org.bouncycastle.util.Arrays;

@AllArgsConstructor
public class RbelJwtSignatureWriter implements RbelElementWriter {

  public static final byte[] VERIFIED_USING_MARKER = "NewVerifiedUsing: ".getBytes(UTF_8);

  static {
    BrainpoolCurves.init();
  }

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelJwtSignature.class);
  }

  @Override
  public byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    if (oldTargetElement.getFacetOrFail(RbelJwtSignature.class).getVerifiedUsing()
        == oldTargetModifiedChild) {
      return Arrays.concatenate(VERIFIED_USING_MARKER, newContent);
    }
    return newContent;
  }
}
