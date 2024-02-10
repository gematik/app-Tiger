/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.matchers;

import static de.gematik.test.tiger.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.model.RequestDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class MatchDifference {

  public enum Field {
    METHOD("method"),
    PATH("path"),
    PATH_PARAMETERS("pathParameters"),
    QUERY_PARAMETERS("queryParameters"),
    COOKIES("cookies"),
    HEADERS("headers"),
    BODY("body"),
    SECURE("secure"),
    PROTOCOL("protocol"),
    KEEP_ALIVE("keep-alive"),
    OPERATION("operation"),
    OPENAPI("openapi");

    private final String name;

    Field(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  private final boolean detailedMatchFailures;
  private final RequestDefinition httpRequest;
  private final Map<Field, List<String>> differences = new ConcurrentHashMap<>();
  private Field fieldName;

  public MatchDifference(boolean detailedMatchFailures, RequestDefinition httpRequest) {
    this.detailedMatchFailures = detailedMatchFailures;
    this.httpRequest = httpRequest;
  }

  public MatchDifference addDifference(Field fieldName, String messageFormat, Object... arguments) {
    if (detailedMatchFailures) {
      if (isNotBlank(messageFormat) && arguments != null && fieldName != null) {
        this.differences
            .computeIfAbsent(fieldName, key -> new ArrayList<>())
            .add(formatLogMessage(1, messageFormat, arguments));
      }
    }
    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  public MatchDifference addDifference(String messageFormat, Object... arguments) {
    return addDifference(fieldName, messageFormat, arguments);
  }

  public RequestDefinition getHttpRequest() {
    return httpRequest;
  }

  public String getLogCorrelationId() {
    return httpRequest.getLogCorrelationId();
  }

  @SuppressWarnings("UnusedReturnValue")
  protected MatchDifference currentField(Field fieldName) {
    this.fieldName = fieldName;
    return this;
  }

  public List<String> getDifferences(Field fieldName) {
    return this.differences.get(fieldName);
  }
}
