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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
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
    if (ArrayUtils.indexOf(rbel.getRawContent(), (byte) '\n') >= 0) {
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
    if (pathParts.length > 1) {
      uriFacetBuilder.queryParameters(
          extractParameterMap(uri, context, rbel.getRawStringContent(), rbel));
    } else {
      uriFacetBuilder.queryParameters(List.of());
    }
    rbel.addFacet(uriFacetBuilder.build());
  }
}
