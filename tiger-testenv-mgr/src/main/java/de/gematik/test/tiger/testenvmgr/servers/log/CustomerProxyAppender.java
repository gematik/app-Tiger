/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogUpdate;

public class CustomerProxyAppender extends AppenderBase<ILoggingEvent> {

    private TigerProxyServer tigerProxyServer;
    private String serverId;

    public CustomerProxyAppender(TigerProxyServer tigerProxyServer) {
       this.tigerProxyServer = tigerProxyServer;
       this.serverId = tigerProxyServer.getServerId();
    }

    /**
     * Send the LogEvent to all Listeners
     * @param iLoggingEvent the LogEventObject
     */
    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        for(TigerServerLogListener listener : tigerProxyServer.getLogListeners()){
            listener.receiveServerLogUpdate(
                TigerServerLogUpdate
                .builder()
                .logLevel(iLoggingEvent.getLevel().levelStr)
                .logMessage(iLoggingEvent.getFormattedMessage())
                .serverName(serverId)
                .build());
        }
    }
}
