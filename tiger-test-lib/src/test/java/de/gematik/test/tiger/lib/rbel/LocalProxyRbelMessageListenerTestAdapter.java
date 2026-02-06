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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelMessageHistory;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;

/**
 * For unit tests that want to test the RbelMessageValidator without starting a full-blown tiger
 * environment. This classes provides a LocalProxyRbelMessageListener with an internal queue of
 * messages that does not require the tiger proxy.
 */
@Getter
public class LocalProxyRbelMessageListenerTestAdapter {

  private final LocalProxyRbelMessageListener localProxyRbelMessageListener;
  private final NavigableMap<Long, RbelElement> validatableMessagesMock;

  public LocalProxyRbelMessageListenerTestAdapter() {
    this.localProxyRbelMessageListener =
        new LocalProxyRbelMessageListener(
            new RbelMessagesSupplier() {
              @Override
              public void addRbelMessageListener(IRbelMessageListener listener) {
                // do nothing
              }

              @Override
              public RbelMessageHistory.Facade getMessageHistory() {
                return new MockHistoryFacade(validatableMessagesMock);
              }
            });
    this.validatableMessagesMock = new TreeMap<>();
  }

  public void clearMockMessagesList() {
    localProxyRbelMessageListener.clearValidatableRbelMessages();
    validatableMessagesMock.clear();
  }

  public void addMessage(RbelElement element) {
    validatableMessagesMock.put(element.getSequenceNumber().get(), element);
  }

  public void addMessages(Collection<RbelElement> elements) {
    elements.forEach(this::addMessage);
  }

  public void addSomeMessagesToTigerTestHooks() {
    final RbelLogger logger =
        RbelLogger.build(
            RbelConfiguration.builder()
                .capturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/testdata/ssoTokenFlow.tgr")
                        .build())
                .build());
    logger.getRbelCapturer().initialize();
    logger.getMessages().forEach(this::addMessage);
  }

  /* we add requests to the given validatableMessagesMock.
   */
  public void addTwoRequestsToTigerTestHooks() {
    localProxyRbelMessageListener.clearValidatableRbelMessages();
    val requestsAndResponses = buildElementsFromTgrFile("simpleHttpRequests.tgr");
    requestsAndResponses.forEach(this::addMessage);
  }

  @SneakyThrows
  public List<RbelElement> buildElementsFromTgrFile(String fileName) {
    val fileContent =
        Files.readString(
            TigerGlobalConfiguration.resolveRelativePathToTigerYaml(".")
                .resolve(Path.of("src", "test", "resources", "testdata", fileName)),
            StandardCharsets.UTF_8);
    return new RbelFileWriter(RbelLogger.build().getRbelConverter())
        .convertFromRbelFile(fileContent, Optional.empty());
  }
}
