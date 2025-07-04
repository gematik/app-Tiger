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
package de.gematik.rbellogger.modifier;

import static java.nio.charset.StandardCharsets.UTF_8;

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.jose.RbelJwtSignature;
import lombok.AllArgsConstructor;
import org.bouncycastle.util.Arrays;

@AllArgsConstructor
public class RbelJwtSignatureWriter implements RbelElementWriter {

  protected static final byte[] VERIFIED_USING_MARKER = "NewVerifiedUsing: ".getBytes(UTF_8);

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
