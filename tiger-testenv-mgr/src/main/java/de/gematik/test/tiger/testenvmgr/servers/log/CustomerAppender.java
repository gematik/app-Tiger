package de.gematik.test.tiger.testenvmgr.servers.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogUpdate;

public class CustomerAppender extends AppenderBase<ILoggingEvent> {

    private TigerServer server;

    public CustomerAppender(TigerServer server) {
       this.server = server;
    }

    /**
     * Send the LogEvent to all Listeners
     * @param iLoggingEvent the LogEventObject
     */
    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        for(TigerServerLogListener listener : server.getLogListeners()){
            listener.receiveServerLogUpdate(TigerServerLogUpdate
                .builder()
                .logLevel(iLoggingEvent.getLevel().levelStr)
                .logMessage(iLoggingEvent.getFormattedMessage())
                .serverName(server.getServerId())
                .build());
        }
    }
}
