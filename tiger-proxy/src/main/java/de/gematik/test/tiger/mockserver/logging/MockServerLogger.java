/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.logging;

import com.google.common.annotations.VisibleForTesting;
import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/*
 * @author jamesdbloom
 */
@Slf4j
public class MockServerLogger {

  static {
    configureLogger();
  }

  public static void configureLogger() {}

  private final Logger logger;
  private HttpState httpStateHandler;

  @VisibleForTesting
  public MockServerLogger() {
    this(MockServerLogger.class);
  }

  @VisibleForTesting
  public MockServerLogger(final Logger logger) {
    this.logger = logger;
    this.httpStateHandler = null;
  }

  public MockServerLogger(final Class<?> loggerClass) {
    this.logger = LoggerFactory.getLogger(loggerClass);
    this.httpStateHandler = null;
  }

  public MockServerLogger(final @Nullable HttpState httpStateHandler) {
    this.logger = null;
    this.httpStateHandler = httpStateHandler;
  }

  public static void writeRequestMessageToLogger(
      Logger log, Level level, Supplier<LogEntry> logEntrySupplier) {
    if (log.isEnabledForLevel(level)) {
      log.atLevel(level).log(logEntrySupplier.get().toString());
    }
  }

  public MockServerLogger setHttpStateHandler(HttpState httpStateHandler) {
    this.httpStateHandler = httpStateHandler;
    return this;
  }

  public void logEvent(LogEntry logEntry) {
    log.atLevel(logEntry.getLogLevel()).log(logEntry.getMessage());
  }

  public static boolean isEnabled(final Level level) {
    return log.isEnabledForLevel(level);
  }

  public static boolean isEnabled(final Level level, final Level configuredLevel) {
    return configuredLevel != null && level.toInt() >= configuredLevel.toInt();
  }
}
