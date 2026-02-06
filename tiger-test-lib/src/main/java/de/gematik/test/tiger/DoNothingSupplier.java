/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger;

import de.gematik.rbellogger.RbelMessageHistory;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.lib.rbel.MockHistoryFacade;
import java.util.TreeMap;

/**
 * When starting the tiger test suite with the local tiger proxy active set to false, there are
 * still code sections that attempt to access the LocalProxyRbelMessageListener. To prevent such
 * access to throw exceptions, we fallback to this supplier
 */
public class DoNothingSupplier implements RbelMessagesSupplier {

  @Override
  public void addRbelMessageListener(IRbelMessageListener listener) {
    // NOOP
  }

  @Override
  public RbelMessageHistory.Facade getMessageHistory() {
    return new MockHistoryFacade(new TreeMap<>());
  }
}
