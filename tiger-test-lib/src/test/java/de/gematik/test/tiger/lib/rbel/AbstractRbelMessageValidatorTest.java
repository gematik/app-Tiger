/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.lib.rbel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

abstract class AbstractRbelMessageValidatorTest {
  protected final TigerProxy tigerProxy = mock(TigerProxy.class);
  protected final Deque<RbelElement> validatableMessagesMock = new ArrayDeque<>();
  protected RbelMessageValidator rbelMessageValidator;
  protected RBelValidatorGlue glue;

  protected void readTgrFileAndStoreForRbelMessageValidator(String rbelFile) {
    readTgrFileAndStoreForRbelMessageValidator(rbelFile, List.of());
  }

  protected void readTgrFileAndStoreForRbelMessageValidator(
      String rbelFile, List<String> activateRbelParsingFor) {
    var rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .setActivateRbelParsingFor(activateRbelParsingFor)
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(RbelFileReaderCapturer.builder().rbelFile(rbelFile).build()));
    rbelLogger.getRbelCapturer().initialize();
    validatableMessagesMock.addAll(rbelLogger.getMessageHistory());
  }

  protected void setUp() {
    TigerGlobalConfiguration.reset();
    validatableMessagesMock.clear();
    LocalProxyRbelMessageListener.setTestingInstance(
        new LocalProxyRbelMessageListener(
            new RbelMessagesSupplier() {
              @Override
              public void addRbelMessageListener(IRbelMessageListener listener) {
                // do nothing
              }

              @Override
              public Deque<RbelElement> getRbelMessages() {
                return validatableMessagesMock;
              }
            }));

    when(tigerProxy.getRbelMessages()).thenReturn(validatableMessagesMock);
    rbelMessageValidator = new RbelMessageValidator(mock(TigerTestEnvMgr.class), tigerProxy);

    rbelMessageValidator.clearCurrentMessages();

    glue = new RBelValidatorGlue(rbelMessageValidator);

    LocalProxyRbelMessageListener.getInstance().clearValidatableRbelMessages();
    RbelMessageValidator.RBEL_REQUEST_TIMEOUT.putValue(1);
  }

  protected static void tearDown() {
    LocalProxyRbelMessageListener.clearTestingInstance();
    RbelMessageValidator.RBEL_REQUEST_TIMEOUT.clearValue();
  }
}
