/*
 * Copyright (c) 2023 gematik GmbH
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

import static de.gematik.rbellogger.converter.RbelHttpRequestConverter.findEolInHttpMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import com.google.common.net.MediaType;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelResponseFacet;
import de.gematik.rbellogger.util.RbelArrayUtils;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelHttpResponseConverter implements RbelConverterPlugin {

    @Override
    public boolean ignoreOversize() {
        return true;
    }

    @Override
    public void consumeElement(final RbelElement targetElement, final RbelConverter converter) {
        final String content = targetElement.getRawStringContent();
        if (!content.startsWith("HTTP")) {
            return;
        }

        String eol = findEolInHttpMessage(content);

        int separator = content.indexOf(eol + eol);
        if (separator == -1) {
            return;
        }
        separator += 2 * eol.length();

        final RbelElement headerElement = extractHeaderFromMessage(targetElement, converter, eol);

        final byte[] bodyData = extractBodyData(targetElement, separator,
            headerElement.getFacetOrFail(RbelHttpHeaderFacet.class), eol);
        final RbelElement bodyElement = new RbelElement(bodyData, targetElement,
            findCharsetInHeader(headerElement.getFacetOrFail(RbelHttpHeaderFacet.class)));
        final RbelElement responseCode = extractResponseCodeFromMessage(targetElement, content);
        final RbelHttpResponseFacet rbelHttpResponse = RbelHttpResponseFacet.builder()
            .responseCode(responseCode)
            .reasonPhrase(extractReasonPhraseFromMessage(targetElement, content))
            .build();

        targetElement.addFacet(rbelHttpResponse);
        targetElement.addFacet(new RbelResponseFacet(responseCode.getRawStringContent()));
        targetElement.addFacet(RbelHttpMessageFacet.builder()
            .header(headerElement)
            .body(bodyElement)
            .build());

        converter.convertElement(bodyElement);
    }

    private RbelElement extractResponseCodeFromMessage(RbelElement targetElement, String content) {
        return RbelElement.builder()
            .parentNode(targetElement)
            .rawContent(content.split("\\s")[1].getBytes(UTF_8))
            .build();
    }

    private RbelElement extractReasonPhraseFromMessage(RbelElement targetElement, String content) {
        String[] responseStatusLineSplit = content.split("\\r\\n")[0].trim().split("\\s", 3);
        if (responseStatusLineSplit.length == 2) {
            return RbelElement.builder()
                .parentNode(targetElement)
                .build();
        } else {
            return RbelElement.builder()
                .parentNode(targetElement)
                .rawContent(responseStatusLineSplit[2].trim().getBytes(targetElement.getElementCharset()))
                .build();
        }
    }

    public RbelElement extractHeaderFromMessage(RbelElement rbel, RbelConverter converter, String eol) {
        final String content = rbel.getRawStringContent();
        int endOfBodyPosition = content.indexOf(eol + eol);
        int endOfFirstLine = content.indexOf(eol) + eol.length();

        if (endOfBodyPosition < 0) {
            endOfBodyPosition = content.length();
        } else {
            endOfBodyPosition += 2 * eol.length();
        }

        final List<String> headerList = Arrays
            .stream(content.substring(endOfFirstLine, endOfBodyPosition).split(eol))
            .filter(line -> !line.isEmpty() && !line.startsWith("HTTP"))
            .collect(Collectors.toList());

        RbelElement headerElement = new RbelElement(
            headerList.stream().collect(Collectors.joining(eol)).getBytes(rbel.getElementCharset()), rbel);
        final RbelMultiMap headerMap = headerList.stream()
            .map(line -> parseStringToKeyValuePair(line, converter, headerElement))
            .collect(RbelMultiMap.COLLECTOR);
        headerElement.addFacet(new RbelHttpHeaderFacet(headerMap));

        return headerElement;
    }

    Optional<Charset> findCharsetInHeader(RbelHttpHeaderFacet headerMap) {
        return headerMap.getCaseInsensitiveMatches("Content-Type")
            .map(RbelElement::getRawStringContent)
            .map(str -> strictParsingOfCharset(str)
                .orElse(guessCharset(str)))
            .findFirst();
    }

    private Charset guessCharset(String str) {
        var lowerCaseString = str.toLowerCase();
        return Stream.of(UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII, StandardCharsets.UTF_16)
            .filter(charset -> charset.aliases().stream()
                .map(String::toLowerCase)
                .anyMatch(lowerCaseString::contains))
            .findFirst()
            .orElse(StandardCharsets.UTF_8);
    }

    private Optional<Charset> strictParsingOfCharset(String s) {
        try {
            return Optional.ofNullable(s)
                .map(MediaType::parse)
                .map(MediaType::charset)
                .filter(com.google.common.base.Optional::isPresent)
                .map(com.google.common.base.Optional::get);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private byte[] extractBodyData(RbelElement rbel, int separator, RbelHttpHeaderFacet headerMap, String eol) {
        byte[] inputData = rbel.getRawContent();

        if (headerMap.hasValueMatching("Transfer-Encoding", "chunked")) {
            separator = rbel.getRawStringContent().indexOf(eol, separator) + eol.length();
            return Arrays.copyOfRange(inputData, Math.min(inputData.length, separator),
                RbelArrayUtils.indexOf(inputData, (eol + "0" + eol).getBytes(rbel.getElementCharset()), separator));
        } else {
            return Arrays.copyOfRange(inputData, Math.min(inputData.length, separator), inputData.length);
        }
    }

    protected SimpleImmutableEntry<String, RbelElement> parseStringToKeyValuePair(final String line,
                                                                                  final RbelConverter context, RbelElement headerElement) {
        final int colon = line.indexOf(':');
        if (colon == -1) {
            throw new IllegalArgumentException("Header malformed: '" + line + "'");
        }
        final String key = line.substring(0, colon).trim();
        final RbelElement el = new RbelElement(
            line.substring(colon + 1).trim().getBytes(headerElement.getElementCharset()), headerElement);

        return new SimpleImmutableEntry<>(key, context.convertElement(el));
    }
}
