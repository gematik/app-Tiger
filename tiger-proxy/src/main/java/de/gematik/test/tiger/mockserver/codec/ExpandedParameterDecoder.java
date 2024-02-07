/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import de.gematik.test.tiger.mockserver.model.Parameters;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;

/*
 * @author jamesdbloom
 */
public class ExpandedParameterDecoder {

  private final Configuration configuration;
  private final MockServerLogger mockServerLogger;

  public ExpandedParameterDecoder(Configuration configuration, MockServerLogger mockServerLogger) {
    this.configuration = configuration;
    this.mockServerLogger = mockServerLogger;
  }

  public Parameters retrieveFormParameters(String parameterString, boolean hasPath) {
    Parameters parameters = new Parameters();
    Map<String, List<String>> parameterMap = new HashMap<>();
    if (isNotBlank(parameterString)) {
      try {
        hasPath = parameterString.startsWith("/") || parameterString.contains("?") || hasPath;
        parameterMap.putAll(
            new QueryStringDecoder(
                    parameterString,
                    HttpConstants.DEFAULT_CHARSET,
                    hasPath,
                    Integer.MAX_VALUE,
                    !configuration.useSemicolonAsQueryParameterSeparator())
                .parameters());
      } catch (IllegalArgumentException iae) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("exception{}while parsing query string{}")
                .setArguments(parameterString, iae.getMessage())
                .setThrowable(iae));
      }
    }
    return parameters.withEntries(parameterMap);
  }

  public Parameters retrieveQueryParameters(String parameterString, boolean hasPath) {
    if (isNotBlank(parameterString)) {
      String rawParameterString =
          parameterString.contains("?")
              ? StringUtils.substringAfter(parameterString, "?")
              : parameterString;
      Map<String, List<String>> parameterMap = new HashMap<>();
      try {
        hasPath = parameterString.startsWith("/") || parameterString.contains("?") || hasPath;
        parameterMap.putAll(
            new QueryStringDecoder(
                    parameterString,
                    HttpConstants.DEFAULT_CHARSET,
                    parameterString.contains("/") || hasPath,
                    Integer.MAX_VALUE,
                    true)
                .parameters());
      } catch (IllegalArgumentException iae) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("exception{}while parsing query string{}")
                .setArguments(parameterString, iae.getMessage())
                .setThrowable(iae));
      }
      return new Parameters().withEntries(parameterMap).withRawParameterString(rawParameterString);
    }
    return null;
  }
}
