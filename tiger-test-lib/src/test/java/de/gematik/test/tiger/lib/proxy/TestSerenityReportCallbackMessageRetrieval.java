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
package de.gematik.test.tiger.lib.proxy;

import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks.StepState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TestSerenityReportCallbackMessageRetrieval {

  @Test
  @TigerTest(
      tigerYaml =
          """
          rbel.request.timeout: 10000
          tigerProxy:
            fileSaveInfo.sourceFile: "src/test/resources/testdata/interleavedRequests.tgr"
          """)
  void parseTgrFile_SerenityCallbackShouldReportNoProblems(TigerTestEnvMgr envMgr) {
    ReflectionTestUtils.setField(TigerDirector.class, "initialized", true);
    ReflectionTestUtils.setField(TigerDirector.class, "tigerTestEnvMgr", envMgr);
    LocalProxyRbelMessageListener.getInstance().clearAllMessages();
    for (RbelElement rbelElement : envMgr.getLocalTigerProxyOrFail().getRbelMessagesList()) {
      LocalProxyRbelMessageListener.getInstance().triggerNewReceivedMessage(rbelElement);
    }

    await()
        .untilAsserted(
            () -> SerenityReporterCallbacks.getCurrentStepMessages(false, StepState.FINISHED));
  }
}
