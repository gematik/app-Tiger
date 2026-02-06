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
package de.gematik.rbellogger.data.core;

import de.gematik.rbellogger.data.RbelElement;
import java.util.Comparator;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public class RbelMismatchNoteFacet extends RbelNoteFacet
    implements Comparable<RbelMismatchNoteFacet> {

  public static final Comparator<RbelMismatchNoteFacet> COMPARATOR =
      Comparator.comparing(RbelMismatchNoteFacet::getMismatchType)
          .thenComparingLong(RbelMismatchNoteFacet::getSequenceNumber)
          .thenComparing(RbelMismatchNoteFacet::getRbelPath)
          .thenComparing(RbelMismatchNoteFacet::getValue);

  private final MismatchType mismatchType;
  private final long sequenceNumber;
  private final String rbelPath;

  @Override
  public int compareTo(RbelMismatchNoteFacet o) {
    return COMPARATOR.compare(this, o);
  }

  public RbelMismatchNoteFacet(
      MismatchType mismatchType, String value, RbelElement mismatchedNode) {
    super(value, NoteStyling.ERROR);
    this.mismatchType = mismatchType;
    this.sequenceNumber = mismatchedNode.getSequenceNumber().orElse(-1L);
    this.rbelPath =
        mismatchedNode.getParentNode() != null ? "$." + mismatchedNode.findNodePath() : "$";
  }

  public void removeFrom(RbelElement element) {
    if (element.getParentNode() != null) {
      element.getFacets().remove(this);
    } else {
      element.findElement(rbelPath).ifPresent(n -> n.getFacets().remove(this));
    }
  }

  public enum MismatchType {
    VALUE_MISMATCH,
    MISSING_NODE,
    WRONG_PATH,
    FILTER_MISMATCH,
    AMBIGUOUS,
    UNKNOWN
  }
}
