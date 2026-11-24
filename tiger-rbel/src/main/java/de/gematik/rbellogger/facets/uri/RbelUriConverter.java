/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.facets.uri;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.uri.RbelUriFacet.RbelUriFacetBuilder;
import de.gematik.rbellogger.util.CharsetUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RbelUriConverter extends RbelConverterPlugin {

  public List<RbelElement> extractParameterMap(
      final URI uri,
      final RbelConversionExecutor context,
      String originalContent,
      RbelElement parentNode) {
    if (StringUtils.isEmpty(uri.getQuery())) {
      return List.of();
    }

    final String[] split = originalContent.split("\\?");
    if (split.length < 2) {
      return List.of();
    }
    return Stream.of(split[1].split("\\&"))
        .filter(StringUtils::isNotEmpty)
        .map(
            param -> {
              RbelElement paramPair =
                  new RbelElement(param.getBytes(parentNode.getElementCharset()), parentNode);
              final String[] splitParams = param.split("\\=", 2);
              if (splitParams.length == 1) {
                paramPair.addFacet(
                    RbelUriParameterFacet.builder()
                        .key(RbelElement.wrap(paramPair, splitParams[0]))
                        .value(context.convertElement("", paramPair))
                        .build());
              } else {
                paramPair.addFacet(
                    RbelUriParameterFacet.builder()
                        .key(RbelElement.wrap(paramPair, splitParams[0]))
                        .value(
                            context.convertElement(
                                URLDecoder.decode(splitParams[1], StandardCharsets.UTF_8)
                                    .getBytes(parentNode.getElementCharset()),
                                paramPair))
                        .build());
              }
              return paramPair;
            })
        .toList();
  }

  private static final int MAX_PREFIX_CHECKED = 4096;

  /**
   * A cheap look at the bytes (only the first {@value #MAX_PREFIX_CHECKED}, new URI judges the
   * rest), to spare most values the decoding to a String and a failing {@code new URI}. It only
   * returns true when {@code java.net.URI} is certain to throw, or when the value has neither a
   * scheme, nor a query, nor a leading slash, so that it would not be taken anyway.
   */
  static boolean isCertainlyNoUri(RbelContent content, Charset charset) {
    if (!CharsetUtils.isAsciiCompatible(charset)) {
      return false;
    }
    final int size = content.size();
    final int end = Math.min(size, MAX_PREFIX_CHECKED);
    final boolean complete = end == size;
    final int delimiter =
        content.indexOfFirst(b -> b == ':' || b == '/' || b == '?' || b == '#', 0, size);
    if (delimiter < 0) {
      return true;
    }
    final byte found = content.get(delimiter);
    if (found == '#') {
      return true;
    }
    if (found == ':') {
      if (!isScheme(content, delimiter)) {
        return true;
      }
    } else if (found == '/'
        && delimiter > 0
        && complete
        && content.indexOfFirst(b -> b == '?', delimiter, end) < 0) {
      return true;
    }
    final int firstHash = content.indexOfFirst(b -> b == '#', 0, end);
    return (firstHash >= 0 && content.indexOfFirst(b -> b == '#', firstHash + 1, end) >= 0)
        || (content.indexOfFirst(b -> b == '[', 0, end) < 0 && hasMalformedEscape(content, end))
        || content.indexOfFirst(RbelUriConverter::isNeverInUri, 0, end) >= 0;
  }

  private static boolean isScheme(RbelContent content, int end) {
    return end > 0
        && isAlpha(content.get(0))
        && content.indexOfFirst(
                b -> !isAlpha(b) && !isDigit(b) && b != '+' && b != '-' && b != '.', 1, end)
            < 0;
  }

  private static boolean hasMalformedEscape(RbelContent content, int end) {
    for (int percent = content.indexOfFirst(b -> b == '%', 0, end);
        percent >= 0;
        percent = content.indexOfFirst(b -> b == '%', percent + 1, end)) {
      if (percent + 2 >= content.size()
          || !isHexDigit(content.get(percent + 1))
          || !isHexDigit(content.get(percent + 2))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNeverInUri(int b) {
    return b == ' ' || b == '"' || b == '<' || b == '>' || b == 0x5C || b == '^' || b == '`'
        || b == '{' || b == '|' || b == '}';
  }

  private static boolean isAlpha(int b) {
    return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z');
  }

  private static boolean isDigit(int b) {
    return b >= '0' && b <= '9';
  }

  private static boolean isHexDigit(int b) {
    return isDigit(b) || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
  }

  private static URI parseUri(String rawStringContent) {
    try {
      final URI uri = new URI(rawStringContent);
      final boolean hasQuery = uri.getQuery() != null;
      final boolean hasProtocol = uri.getScheme() != null;
      if (hasQuery || hasProtocol || rawStringContent.startsWith("/")) {
        return uri;
      }
    } catch (URISyntaxException ignored) {
      // ignore
    }
    return null;
  }

  @Override
  public void consumeElement(final RbelElement rbel, final RbelConversionExecutor context) {
    final RbelContent rawContent = rbel.getContent();
    if (rawContent.isNull()
        || isCertainlyNoUri(rawContent, rbel.getElementCharset())
        || rawContent.indexOfFirst(Character::isISOControl, 0, rawContent.size()) >= 0) {
      return;
    }
    var content = rbel.getRawStringContent();
    if (content == null) {
      return;
    }
    URI uri = parseUri(content);
    if (uri == null) {
      return;
    }
    final String[] pathParts = content.split("\\?", 2);
    final RbelUriFacetBuilder uriFacetBuilder =
        RbelUriFacet.builder().basicPath(RbelElement.wrap(rbel, pathParts[0]));
    if (uri.getScheme() != null) {
      uriFacetBuilder.scheme(RbelElement.wrap(rbel, uri.getScheme()));
    }
    if (uri.getAuthority() != null) {
      uriFacetBuilder.authority(RbelElement.wrap(rbel, uri.getAuthority()));
    }
    if (uri.getUserInfo() != null) {
      uriFacetBuilder.userInfo(RbelElement.wrap(rbel, uri.getUserInfo()));
    }
    if (uri.getPath() != null) {
      uriFacetBuilder.path(RbelElement.wrap(rbel, uri.getPath()));
    }
    if (uri.getQuery() != null) {
      uriFacetBuilder.query(RbelElement.wrap(rbel, uri.getQuery()));
    }
    if (uri.getHost() != null) {
      uriFacetBuilder.host(RbelElement.wrap(rbel, uri.getHost()));
    }
    if (uri.getPort() >= 0) {
      uriFacetBuilder.port(RbelElement.wrap(rbel, uri.getPort()));
    }
    if (pathParts.length > 1) {
      uriFacetBuilder.queryParameters(
          extractParameterMap(uri, context, rbel.getRawStringContent(), rbel));
    } else {
      uriFacetBuilder.queryParameters(List.of());
    }
    rbel.addFacet(uriFacetBuilder.build());
  }
}
