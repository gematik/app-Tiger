/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr.servers.log;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import java.io.File;
import org.slf4j.LoggerFactory;

public class TigerServerLogManager  {

    private static final String DEFAULT_LOGFILE_LOCATION = "./target/serverLogs/";
    private static final String LOGFILE_EXTENSION = ".log";
    private static final String DEFAULT_PATTERN_LAYOUT = "%date %level [%thread] %logger{10} [%file:%line] %msg%n";

    public static void addAppenders(TigerServer server) {
        ch.qos.logback.classic.Logger logbackLogger = ((ch.qos.logback.classic.Logger)server.getLog());
        createAndAddAppenders(server, logbackLogger);
    }

    private static void createAndAddAppenders(TigerServer server, ch.qos.logback.classic.Logger log) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setPattern(DEFAULT_PATTERN_LAYOUT);
        patternLayoutEncoder.setContext(loggerContext);
        patternLayoutEncoder.start();

        log.addAppender(createFileAppender(server.getServerId(), server.getConfiguration().getLogFile(), loggerContext, patternLayoutEncoder));
        log.addAppender(createConsoleAppender(loggerContext, patternLayoutEncoder));
        log.addAppender(createCustomerAppender(server, loggerContext));

        log.setAdditive(false);
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

    private static CustomerAppender createCustomerAppender(TigerServer server, LoggerContext loggerContext) {
        CustomerAppender customerAppender = new CustomerAppender(server);
        customerAppender.setContext(loggerContext);
        customerAppender.start();

        return customerAppender;
    }
}
