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
package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.env.TestEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePushController implements TigerUpdateListener {

  public final SimpMessagingTemplate template;
  public final TigerTestEnvMgr tigerTestEnvMgr;

  @PostConstruct
  public void addWebSocketListener() {
    tigerTestEnvMgr.registerNewListener(this);
  }

  @Override
  public void receiveTestEnvUpdate(TigerStatusUpdate update) {
    if (tigerTestEnvMgr.isWorkflowUiSentFetch() && !tigerTestEnvMgr.isShouldAbortTestExecution()) {
      log.trace("Propagating status udpate {}", update);
      template.convertAndSend("/topic/envStatus", TestEnvStatusDto.createFrom(update));
    }
  }
}
