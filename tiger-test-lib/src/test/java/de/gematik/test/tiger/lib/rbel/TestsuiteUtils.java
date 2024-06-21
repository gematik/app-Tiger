/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
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
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import org.apache.commons.io.FileUtils;

public class TestsuiteUtils {

  public static void addSomeMessagesToTigerTestHooks(Deque<RbelElement> validatableMessagesMock) {
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

  /* we add requests to the given validatableMessagesMock. The calling code must ensure that
  this validatableMessagesMock is returned by the TigerProxy.getRbelMessages() method.
   */
  public static void addTwoRequestsToTigerTestHooks(Deque<RbelElement> validatableMessagesMock) {
    TigerGlobalConfiguration.putValue("tiger.rbel.request.timeout", 1);
    LocalProxyRbelMessageListener.getInstance().clearValidatableRbelMessages();
    RbelElement request = buildRequestFromCurlFile("getRequestLocalhost.curl");
    validatableMessagesMock.add(request);
    validatableMessagesMock.add(buildResponseFromCurlFile("htmlMessage.curl", request));
    request = buildRequestFromCurlFile("getRequestEitzenAt.curl");
    validatableMessagesMock.add(request);
    validatableMessagesMock.add(buildResponseFromCurlFile("htmlMessageEitzenAt.curl", request));
  }

  public static RbelElement buildRequestFromCurlFile(String curlFileName) {
    String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(curlFileName, StandardCharsets.UTF_8);
    return RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
  }

  public static RbelElement buildResponseFromCurlFile(String curlFileName, RbelElement request) {
    String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(curlFileName, StandardCharsets.UTF_8);
    RbelElement message = RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
    message.addOrReplaceFacet(
        message.getFacet(RbelHttpResponseFacet.class).get().toBuilder().request(request).build());
    return message;
  }

  public static String readCurlFromFileWithCorrectedLineBreaks(String fileName, Charset charset) {
    try {
      return FileUtils.readFileToString(
              new File("src/test/resources/testdata/sampleCurlMessages/" + fileName), charset)
          .replaceAll("(?<!\\r)\\n", "\r\n");
    } catch (IOException ioe) {
      throw new RuntimeException("Unable to read curl file", ioe);
    }
  }
}
