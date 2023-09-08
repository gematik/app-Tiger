/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Slf4j
public class TigerServerLogManager  {

    private static final String DEFAULT_LOGFILE_LOCATION = "./target/serverLogs/";
    private static final String LOGFILE_EXTENSION = ".log";
    private static final String DEFAULT_PATTERN_LAYOUT = "%date %level [%thread] %logger{10} [%file:%line] %msg%n";

    public static void addAppenders(AbstractTigerServer server) {
        Logger logbackLogger = server.getLog();
        createAndAddAppenders(server, logbackLogger);
    }

    public static void setLoggingLevel(String loggerName, String levelString) {
        log.debug("Changing loglevel for '{}' to ({})", loggerName, levelString);
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(loggerName);
        logger.setLevel(ch.qos.logback.classic.Level.toLevel(levelString.toUpperCase(), Level.INFO));
    }

    private static void createAndAddAppenders(AbstractTigerServer server, Logger log) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setPattern(DEFAULT_PATTERN_LAYOUT);
        patternLayoutEncoder.setContext(loggerContext);
        patternLayoutEncoder.start();

        if (server.getConfiguration().getExternalJarOptions() == null ||
            (server.getConfiguration().getExternalJarOptions().isActivateLogs())) {
            ((ch.qos.logback.classic.Logger) log).addAppender(
                createFileAppender(server.getServerId(), server.getConfiguration().getLogFile(), loggerContext,
                    patternLayoutEncoder));
            ((ch.qos.logback.classic.Logger) log).addAppender(
                createConsoleAppender(loggerContext, patternLayoutEncoder));
            ((ch.qos.logback.classic.Logger) log).addAppender(createCustomerAppender(server, loggerContext));
        }
        ((ch.qos.logback.classic.Logger) log).setAdditive(false);
    }

    private static FileAppender<ILoggingEvent> createFileAppender(String serverId, String logFilePath, LoggerContext loggerContext, PatternLayoutEncoder patternLayoutEncoder) {
        String fileName = createFileName(serverId, logFilePath);
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setFile(fileName);
        fileAppender.setAppend(false);
        fileAppender.setEncoder(patternLayoutEncoder);
        fileAppender.setContext(loggerContext);
        fileAppender.start();

        return fileAppender;
    }
    private static ConsoleAppender<ILoggingEvent> createConsoleAppender(LoggerContext loggerContext, PatternLayoutEncoder patternLayoutEncoder) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setEncoder(patternLayoutEncoder);
        consoleAppender.setContext(loggerContext);
        consoleAppender.start();

        return consoleAppender;
    }

    private static String createFileName(String serverId, String logFilePath) {
        if (logFilePath != null) {
            File file = new File(logFilePath);
            if (file.isDirectory()) {
                return file.getAbsolutePath() + serverId + LOGFILE_EXTENSION;
            } else {
                return file.getAbsolutePath();
            }
        }
        return DEFAULT_LOGFILE_LOCATION + serverId + LOGFILE_EXTENSION;
    }

    private static CustomerAppender createCustomerAppender(AbstractTigerServer server, LoggerContext loggerContext) {
        CustomerAppender customerAppender = new CustomerAppender(server);
        customerAppender.setContext(loggerContext);
        customerAppender.start();

        return customerAppender;
    }

    public static void addProxyCustomerAppender(TigerProxyServer tigerProxyServer) {
        ch.qos.logback.classic.Logger logbackLogger = ((ch.qos.logback.classic.Logger)tigerProxyServer.getTigerProxy().getLog());
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        CustomerProxyServerAppender customerAppender = new CustomerProxyServerAppender(tigerProxyServer);
        customerAppender.setContext(loggerContext);
        customerAppender.start();
        logbackLogger.addAppender(customerAppender);
    }

    public static void addProxyCustomerAppender(TigerTestEnvMgr tigerTestEnvMgr, Logger log) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        CustomerLocalProxyAppender customerAppender = new CustomerLocalProxyAppender(tigerTestEnvMgr);
        customerAppender.setContext(loggerContext);
        customerAppender.start();
        ((ch.qos.logback.classic.Logger) log).addAppender(customerAppender);
    }
}
