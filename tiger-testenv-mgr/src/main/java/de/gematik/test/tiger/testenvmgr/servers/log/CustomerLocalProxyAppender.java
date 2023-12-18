/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogUpdate;
import java.util.Optional;

public class CustomerLocalProxyAppender extends AppenderBase<ILoggingEvent> {
  private TigerTestEnvMgr tigerTestEnvMgr;

  public CustomerLocalProxyAppender(TigerTestEnvMgr tigerTestEnvMgr) {
    this.tigerTestEnvMgr = tigerTestEnvMgr;
  }

  /**
   * Send the LogEvent to all Listeners
   *
   * @param iLoggingEvent the LogEventObject
   */
  @Override
  protected void append(ILoggingEvent iLoggingEvent) {
    Optional.ofNullable(tigerTestEnvMgr)
        .ifPresent(
            mgr ->
                mgr.getLogListeners()
                    .forEach(
                        listener ->
                            listener.receiveServerLogUpdate(
                                TigerServerLogUpdate.builder()
                                    .logLevel(iLoggingEvent.getLevel().levelStr)
                                    .logMessage(iLoggingEvent.getFormattedMessage())
                                    .serverName("localTigerProxy")
                                    .build())));
  }
}
