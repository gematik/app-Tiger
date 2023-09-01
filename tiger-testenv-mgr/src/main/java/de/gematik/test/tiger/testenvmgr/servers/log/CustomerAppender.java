/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
     * @param iLoggingEvent the LogEventObject
     */
    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        if (server.getConfiguration().getExternalJarOptions() == null || server.getConfiguration().getExternalJarOptions().isActivateLogs()) {
            server.getLogListeners().forEach(listener -> {
                 listener.receiveServerLogUpdate(TigerServerLogUpdate
                    .builder()
                    .logLevel(iLoggingEvent.getLevel().levelStr)
                    .logMessage(iLoggingEvent.getFormattedMessage())
                    .serverName(server.getServerId())
                    .build());
            });
        }
    }
}
