/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
public class ExactStringMatcher extends BodyMatcher<String> {
  private static final String[] excludedFields = {"mockServerLogger"};
  private final MockServerLogger mockServerLogger;
  private final String matcher;

  ExactStringMatcher(MockServerLogger mockServerLogger, String matcher) {
    this.mockServerLogger = mockServerLogger;
    this.matcher = matcher;
  }

  public static boolean matches(String matcher, String matched, boolean ignoreCase) {

    if (StringUtils.isBlank(matcher)) {
      return true;
    } else if (matched != null) {
      if (matched.equals(matcher)) {
        return true;
      }
      // case-insensitive comparison is mainly to improve matching in web containers like Tomcat
      // that convert header names to lower case
      if (ignoreCase) {
        return matched.equalsIgnoreCase(matcher);
      }
    }

    return false;
  }

  public boolean matches(final MatchDifference context, String matched) {
    boolean result = false;

    if (matcher == null) {
      return true;
    }

    if (matched != null && matches(matcher, matched, false)) {
      result = true;
    }

    if (!result && context != null) {
      context.addDifference(
          mockServerLogger, "exact string match failed expected:{}found:{}", this.matcher, matched);
    }

    if (matched == null) {
      return false;
    }

    return result;
  }

  public boolean isBlank() {
    return matcher == null || StringUtils.isBlank(matcher);
  }

  @Override
  @JsonIgnore
  public String[] fieldsExcludedFromEqualsAndHashCode() {
    return excludedFields;
  }
}
