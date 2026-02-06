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
package de.gematik.test.tiger.lib.rbel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.util.List;

abstract class AbstractRbelMessageValidatorTest {
  protected RbelMessageRetriever rbelMessageRetriever;
  protected RbelValidator rbelValidator = new RbelValidator();
  protected RBelValidatorGlue glue;
  protected LocalProxyRbelMessageListenerTestAdapter localProxyRbelMessageListenerTestAdapter;
  protected TigerProxy tigerProxy;

  protected void readTgrFileAndStoreForRbelMessageRetriever(String rbelFile) {
    readTgrFileAndStoreForRbelMessageRetriever(rbelFile, List.of());
  }

  protected void readTgrFileAndStoreForRbelMessageRetriever(
      String rbelFile, List<String> activateRbelParsingFor) {
    var rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .setActivateRbelParsingFor(activateRbelParsingFor)
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(RbelFileReaderCapturer.builder().rbelFile(rbelFile).build()));
    rbelLogger.getRbelCapturer().initialize();
    localProxyRbelMessageListenerTestAdapter.addMessages(rbelLogger.getMessages());
  }

  protected void setUp() {
    TigerGlobalConfiguration.reset();

    this.localProxyRbelMessageListenerTestAdapter = new LocalProxyRbelMessageListenerTestAdapter();

    tigerProxy = mock(TigerProxy.class);
    when(tigerProxy.getMessageHistory())
        .thenReturn(
            new MockHistoryFacade(
                localProxyRbelMessageListenerTestAdapter.getValidatableMessagesMock()));

    rbelMessageRetriever =
        new RbelMessageRetriever(
            mock(TigerTestEnvMgr.class),
            tigerProxy,
            localProxyRbelMessageListenerTestAdapter.getLocalProxyRbelMessageListener());

    rbelMessageRetriever.clearCurrentMessages();
    glue = new RBelValidatorGlue(rbelMessageRetriever);
  }
}
