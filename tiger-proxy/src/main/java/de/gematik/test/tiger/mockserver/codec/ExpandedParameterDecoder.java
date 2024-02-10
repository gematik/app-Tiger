/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.model.Parameters;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Slf4j
public class ExpandedParameterDecoder {

  private final Configuration configuration;

  public ExpandedParameterDecoder(Configuration configuration) {
    this.configuration = configuration;
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
        log.error("exception{}while parsing query string{}", parameterString, iae.getMessage(), iae);
      }
      return new Parameters().withEntries(parameterMap).withRawParameterString(rawParameterString);
    }
    return null;
  }
}
