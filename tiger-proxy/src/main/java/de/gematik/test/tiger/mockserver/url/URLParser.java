/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.url;

import static org.apache.commons.lang3.StringUtils.substringBefore;

/*
 * @author jamesdbloom
 */
public class URLParser {

  private static final String schemeRegex = "https?://.*";
  private static final String schemeHostAndPortRegex =
      "https?://([A-z0-9-_.:]*@)?[A-z0-9-_.]*(:[0-9]*)?";

  public static boolean isFullUrl(String uri) {
    return uri != null && uri.matches(schemeRegex);
  }

  public static String returnPath(String path) {
    String result;
    if (URLParser.isFullUrl(path)) {
      result = path.replaceAll(schemeHostAndPortRegex, "");
    } else {
      result = path;
    }
    return substringBefore(result, "?");
  }
}
