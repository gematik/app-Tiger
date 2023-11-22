/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.TigerServerLogDto;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogUpdate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateLogController implements TigerServerLogListener {

  public final SimpMessagingTemplate template;
  public final TigerTestEnvMgr tigerTestEnvMgr;

  @PostConstruct
  public void addWebSocketListener() {
    tigerTestEnvMgr.getServers().values().forEach(server -> server.registerLogListener(this));
    tigerTestEnvMgr.registerLogListener(this);
  }

  @Override
  public void receiveServerLogUpdate(TigerServerLogUpdate update) {
    if (tigerTestEnvMgr.isWorkflowUiSentFetch() && !tigerTestEnvMgr.isShouldAbortTestExecution()) {
      log.trace("Propagating tiger server log update {}", update);
      template.convertAndSend("/topic/serverLog", TigerServerLogDto.createFrom(update));
    }
  }
}
