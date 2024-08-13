/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
