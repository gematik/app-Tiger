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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelContent;

public interface RbelElementWriter {
  boolean canWrite(RbelElement oldTargetElement);

  /**
   * Writers that need the content as an array implement {@link #write(RbelElement, RbelElement,
   * byte[])} instead; writers that can work on the content directly override this method.
   */
  default RbelContent write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, RbelContent newContent) {
    return RbelContent.wrap(
        write(oldTargetElement, oldTargetModifiedChild, newContent.toByteArray()));
  }

  default byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    throw new UnsupportedOperationException(
        getClass().getName()
            + " must implement write(RbelElement, RbelElement, RbelContent) or write(RbelElement,"
            + " RbelElement, byte[])");
  }
}
