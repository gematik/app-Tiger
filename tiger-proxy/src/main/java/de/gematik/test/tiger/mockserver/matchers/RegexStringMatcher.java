package de.gematik.test.tiger.mockserver.matchers;

import static org.slf4j.event.Level.DEBUG;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.StringUtils;

public class RegexStringMatcher extends BodyMatcher<String> {

  private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
  private final MockServerLogger mockServerLogger;
  private final String matcher;
  private final boolean controlPlaneMatcher;

  public RegexStringMatcher(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
    this.mockServerLogger = mockServerLogger;
    this.controlPlaneMatcher = controlPlaneMatcher;
    this.matcher = null;
  }

  RegexStringMatcher(
      MockServerLogger mockServerLogger, String matcher, boolean controlPlaneMatcher) {
    this.mockServerLogger = mockServerLogger;
    this.controlPlaneMatcher = controlPlaneMatcher;
    this.matcher = matcher;
  }

  public boolean matches(String matched) {
    return matches((MatchDifference) null, matched);
  }

  public boolean matches(final MatchDifference context, String matched) {
    boolean result = matcher == null || matches(mockServerLogger, context, matcher, matched);
    return not != result;
  }

  public boolean matches(String matcher, String matched) {
    return matches(mockServerLogger, null, matcher, matched);
  }

  public boolean matches(
      MockServerLogger mockServerLogger,
      MatchDifference context,
      String matcherValue,
      String matchedValue) {
    if (matcherValue == null) {
      return true;
    }
    if (StringUtils.isBlank(matcherValue)) {
      return true;
    } else {
      if (matchedValue != null) {
        // match as exact string
        if (matchedValue.equals(matcherValue) || matchedValue.equalsIgnoreCase(matcherValue)) {
          return true;
        }

        // match as regex - matcher -> matched (data plane or control plane)
        try {
          if (matchedValue.matches(matcherValue)) {
            return true;
          }
        } catch (PatternSyntaxException pse) {
          if (MockServerLogger.isEnabled(DEBUG) && mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMessageFormat(
                        "error while matching regex ["
                            + matcher
                            + "] for string ["
                            + matchedValue
                            + "] "
                            + pse.getMessage())
                    .setThrowable(pse));
          }
        }
        // match as regex - matched -> matcher (control plane only)
        try {
          if (controlPlaneMatcher && matcherValue.matches(matchedValue)) {
            return true;
          } else if (MockServerLogger.isEnabled(DEBUG)
              && matcherValue.matches(matchedValue)
              && mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMessageFormat("matcher{}would match{}if matcher was used for control plane")
                    .setArguments(matcher, matchedValue));
          }
        } catch (PatternSyntaxException pse) {
          if (controlPlaneMatcher) {
            if (MockServerLogger.isEnabled(DEBUG) && mockServerLogger != null) {
              mockServerLogger.logEvent(
                  new LogEntry()
                      .setLogLevel(DEBUG)
                      .setMessageFormat(
                          "error while matching regex ["
                              + matchedValue
                              + "] for string ["
                              + matcher
                              + "] "
                              + pse.getMessage())
                      .setThrowable(pse));
            }
          }
        }
      }
    }
    if (context != null) {
      context.addDifference(
          mockServerLogger,
          "string or regex match failed expected:{}found:{}",
          matcher,
          matchedValue);
    }

    return false;
  }

  public boolean isBlank() {
    return matcher == null || StringUtils.isBlank(matcher);
  }

  @Override
  @JsonIgnore
  protected String[] fieldsExcludedFromEqualsAndHashCode() {
    return EXCLUDED_FIELDS;
  }
}
