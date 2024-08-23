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

package de.gematik.test.tiger.testenvmgr.servers.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogUpdate;

public class CustomerAppender extends AppenderBase<ILoggingEvent> {

  private final AbstractTigerServer server;

  public CustomerAppender(AbstractTigerServer server) {
    this.server = server;
  }

  /**
   * Send the LogEvent to all Listeners
   *
   * @param iLoggingEvent the LogEventObject
   */
  @Override
  protected void append(ILoggingEvent iLoggingEvent) {
    if (server.getConfiguration().getExternalJarOptions() == null
        || server.getConfiguration().getExternalJarOptions().isActivateWorkflowLogs()) {
      server
          .getLogListeners()
          .forEach(
              listener ->
                  listener.receiveServerLogUpdate(
                      TigerServerLogUpdate.builder()
                          .logLevel(iLoggingEvent.getLevel().levelStr)
                          .logMessage(iLoggingEvent.getFormattedMessage())
                          .serverName(server.getServerId())
                          .build()));
    }
  }
}
