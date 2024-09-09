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
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.util.List;

abstract class AbstractRbelMessageValidatorTest {
  protected RbelMessageValidator rbelMessageValidator;
  protected RBelValidatorGlue glue;
  protected LocalProxyRbelMessageListenerTestAdapter localProxyRbelMessageListenerTestAdapter;
  protected TigerProxy tigerProxy;

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
    localProxyRbelMessageListenerTestAdapter.addMessages(rbelLogger.getMessageHistory());
  }

  protected void setUp() {
    TigerGlobalConfiguration.reset();

    this.localProxyRbelMessageListenerTestAdapter = new LocalProxyRbelMessageListenerTestAdapter();

    tigerProxy = mock(TigerProxy.class);
    when(tigerProxy.getRbelMessages())
        .thenReturn(localProxyRbelMessageListenerTestAdapter.getValidatableMessagesMock());

    rbelMessageValidator =
        new RbelMessageValidator(
            mock(TigerTestEnvMgr.class),
            tigerProxy,
            localProxyRbelMessageListenerTestAdapter.getLocalProxyRbelMessageListener());

    rbelMessageValidator.clearCurrentMessages();
    glue = new RBelValidatorGlue(rbelMessageValidator);
  }
}
