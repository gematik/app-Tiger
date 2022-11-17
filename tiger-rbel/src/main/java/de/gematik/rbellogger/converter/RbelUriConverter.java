/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.data.facet.RbelUriFacet.RbelUriFacetBuilder;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RbelUriConverter implements RbelConverterPlugin {

    public List<RbelElement> extractParameterMap(final URI uri, final RbelConverter context,
                                                 String originalContent, RbelElement parentNode) {
        if (StringUtils.isEmpty(uri.getQuery())) {
            return List.of();
        }

        return Stream.of(originalContent.split("\\?")[1].split("\\&"))
            .filter(StringUtils::isNotEmpty)
            .map(param -> {
                RbelElement paramPair = new RbelElement(param.getBytes(parentNode.getElementCharset()), parentNode);
                final String[] splitParams = param.split("\\=", 2);
                if (splitParams.length == 1) {
                    paramPair.addFacet(RbelUriParameterFacet.builder()
                        .key(RbelElement.wrap(paramPair, splitParams[0]))
                        .value(context.convertElement("", paramPair))
                        .build());
                } else {
                    paramPair.addFacet(RbelUriParameterFacet.builder()
                        .key(RbelElement.wrap(paramPair, splitParams[0]))
                        .value(context.convertElement(
                            URLDecoder.decode(splitParams[1], StandardCharsets.UTF_8).getBytes(parentNode.getElementCharset()), paramPair))
                        .build());
                }
                return paramPair;
            })
            .collect(Collectors.toList());
    }

    public boolean canConvertElement(final RbelElement rbel) {
        try {
            final URI uri = new URI(rbel.getRawStringContent());
            final boolean hasQuery = uri.getQuery() != null;
            final boolean hasProtocol = uri.getScheme() != null;
            return hasQuery || hasProtocol || rbel.getRawStringContent().startsWith("/");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public void consumeElement(final RbelElement rbel, final RbelConverter context) {
        if (!canConvertElement(rbel)) {
            return;
        }
        final URI uri = convertToUri(rbel);

        final String[] pathParts = rbel.getRawStringContent().split("\\?", 2);
        final RbelUriFacetBuilder uriFacetBuilder = RbelUriFacet.builder()
            .basicPath(RbelElement.wrap(rbel, pathParts[0]));
        if (pathParts.length > 1) {
            uriFacetBuilder.queryParameters(extractParameterMap(uri, context, rbel.getRawStringContent(), rbel));
        } else {
            uriFacetBuilder.queryParameters(List.of());
        }
        rbel.addFacet(uriFacetBuilder.build());
    }

    private URI convertToUri(RbelElement target) {
        try {
            return new URI(target.getRawStringContent());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to convert Path-Element", e);
        }
    }
}
