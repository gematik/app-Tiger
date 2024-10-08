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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.io.FileUtils;

/**
 * For unit tests that want to test the RbelMessageValidator without starting a full blown tiger
 * environment. This classes provides a LocalProxyRbelMessageListener with an internal queue of
 * messages that does not require the tiger proxy.
 */
@Getter
public class LocalProxyRbelMessageListenerTestAdapter {

  private final LocalProxyRbelMessageListener localProxyRbelMessageListener;
  private final Deque<RbelElement> validatableMessagesMock;

  public LocalProxyRbelMessageListenerTestAdapter() {
    this.localProxyRbelMessageListener =
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
            });
    this.validatableMessagesMock = new ArrayDeque<>();
  }

  public void clearMockMessagesList() {
    localProxyRbelMessageListener.clearValidatableRbelMessages();
    validatableMessagesMock.clear();
  }

  public void addMessage(RbelElement element) {
    validatableMessagesMock.add(element);
  }

  public void addMessages(Collection<RbelElement> elements) {
    validatableMessagesMock.addAll(elements);
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
    validatableMessagesMock.addAll(logger.getMessageHistory());
  }

  /* we add requests to the given validatableMessagesMock.
   */
  public void addTwoRequestsToTigerTestHooks() {
    localProxyRbelMessageListener.clearValidatableRbelMessages();
    val requestsAndResponses = buildElementsFromTgrFile("simpleHttpRequests.tgr");
    validatableMessagesMock.addAll(requestsAndResponses);
  }

  public RbelElement buildRequestFromCurlFile(String curlFileName) {
    String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(curlFileName, StandardCharsets.UTF_8);
    return RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
  }

  @SneakyThrows
  public List<RbelElement> buildElementsFromTgrFile(String fileName) {
    val fileContent =
        Files.readString(
            Path.of("src", "test", "resources", "testdata", fileName), StandardCharsets.UTF_8);
    return new RbelFileWriter(RbelLogger.build().getRbelConverter())
        .convertFromRbelFile(fileContent);
  }

  public RbelElement buildResponseFromCurlFile(String curlFileName, RbelElement request) {
    String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(curlFileName, StandardCharsets.UTF_8);
    RbelElement message = RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
    message.addOrReplaceFacet(
        message.getFacet(RbelHttpResponseFacet.class).get().toBuilder().request(request).build());
    return message;
  }

  public String readCurlFromFileWithCorrectedLineBreaks(String fileName, Charset charset) {
    try {
      return FileUtils.readFileToString(
              new File("src/test/resources/testdata/sampleCurlMessages/" + fileName), charset)
          .replaceAll("(?<!\\r)\\n", "\r\n");
    } catch (IOException ioe) {
      throw new RuntimeException("Unable to read curl file", ioe);
    }
  }
}
