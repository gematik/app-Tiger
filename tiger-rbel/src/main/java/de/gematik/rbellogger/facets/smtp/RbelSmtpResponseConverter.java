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
package de.gematik.rbellogger.facets.smtp;

import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF;
import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF_BYTES;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@ConverterInfo(onlyActivateFor = "smtp")
public class RbelSmtpResponseConverter extends RbelConverterPlugin {

  public static final Pattern SMTP_RESPONSE =
      Pattern.compile("\\d{3}((-[^\r]*\r\n\\d{3})*( [^\r]+\r\n)|\r\n).*", Pattern.DOTALL);
  private static final Pattern SMTP_MULTI_LINE_RESPONSE =
      Pattern.compile("(?<status>\\d{3})-.+", Pattern.DOTALL);
  private static final Pattern SMTP_SINGLE_LINE_RESPONSE =
      Pattern.compile("(?<status>\\d{3})( (?<body>[^\r]+)|)\r\n.*", Pattern.DOTALL);
  public static final int MIN_SMTP_RESPONSE_LENGTH = 4;

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    buildSmtpResponseFacet(element)
        .ifPresent(
            facetAndLength -> {
              var facet = facetAndLength.getLeft();
              var length = facetAndLength.getRight();
              element.setUsedBytes(length);
              element.addFacet(facet);
              element.addFacet(new RbelRootFacet<>(facet));
              element.addFacet(new RbelResponseFacet(facet.getStatus().getRawStringContent()));
              context.findAndPairMatchingRequest(element, RbelSmtpCommandFacet.class);
            });
  }

  private Optional<Pair<RbelSmtpResponseFacet, Integer>> buildSmtpResponseFacet(
      RbelElement element) {
    return Optional.ofNullable(element.getContent())
        .filter(c -> c.size() > MIN_SMTP_RESPONSE_LENGTH)
        .filter(c -> c.indexOf(CRLF.getBytes()) > 0)
        .filter(this::startsWithResponseCode)
        .map(RbelContent::toByteArray)
        .map(c -> new String(c, StandardCharsets.UTF_8))
        .filter(s -> SMTP_RESPONSE.matcher(s).matches())
        .flatMap(s -> parseSmtpResponse(element, s));
  }

  private boolean startsWithResponseCode(RbelContent content) {
    if (!(Character.isDigit(content.get(0))
        && Character.isDigit(content.get(1))
        && Character.isDigit(content.get(2)))) {
      return false;
    }
    int c = content.get(3);
    return c == ' ' || c == '\r' || c == '-';
  }

  private Optional<Pair<RbelSmtpResponseFacet, Integer>> parseSmtpResponse(
      RbelElement element, String response) {
    String status;
    Optional<String> body;
    int length;

    var multiResponse = SMTP_MULTI_LINE_RESPONSE.matcher(response);
    if (multiResponse.matches()) {
      status = multiResponse.group("status");
      var bodyAndLength = parseMultilineResponse(response, status);
      if (bodyAndLength.isEmpty()) {
        return Optional.empty();
      }
      body = bodyAndLength.map(Pair::getLeft);
      length = bodyAndLength.map(Pair::getRight).orElseThrow();
    } else {
      var singleResponse = SMTP_SINGLE_LINE_RESPONSE.matcher(response);
      if (singleResponse.matches()) {
        status = singleResponse.group("status");
        body = Optional.ofNullable(singleResponse.group("body"));
        length = element.getContent().indexOf(CRLF_BYTES) + CRLF.length();
      } else {
        log.atDebug()
            .addArgument(() -> StringUtils.abbreviate(response, 300))
            .log("unknown SMTP response syntax: {}");
        return Optional.empty();
      }
    }

    RbelElement bodyElement =
        body.map(bodyString -> RbelElement.wrap(element, bodyString)).orElse(null);

    return Optional.of(
        Pair.of(
            RbelSmtpResponseFacet.builder()
                .status(RbelElement.wrap(element, status))
                .body(bodyElement)
                .build(),
            length));
  }

  private static Optional<Pair<String, Integer>> parseMultilineResponse(
      String response, String status) {
    // multiline responses look like this:
    // <status>-<text><CRLF>
    // ...
    // <status>-<text><CRLF>
    // <status> <text><CRLF>
    StringBuilder body = new StringBuilder();
    var lines = response.split(CRLF, 0);
    var multilinePrefix = status + "-";
    var prefixLength = multilinePrefix.length();
    String lastLine = null;
    int length = 0;
    for (String line : lines) {
      lastLine = line;
      if (!line.startsWith(multilinePrefix)) {
        break;
      } else {
        body.append(line.substring(prefixLength));
        body.append(CRLF);
        length += line.length() + CRLF.length();
      }
    }
    if (lastLine == null) {
      return Optional.empty();
    }
    var lastLineParts = lastLine.split(" ", 2);
    if (!lastLineParts[0].equals(status) || lastLineParts.length != 2) {
      return Optional.empty();
    }
    body.append(lastLineParts[1]);
    body.append(CRLF);
    length += lastLine.length() + CRLF.length();
    return Optional.of(Pair.of(body.toString(), length));
  }
}
