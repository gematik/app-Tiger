/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelSmtpResponseFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.util.EmailConversionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConverterInfo(onlyActivateFor = "smtp")
public class RbelSmtpResponseConverter implements RbelConverterPlugin {

  public static final Pattern SMTP_RESPONSE =
      Pattern.compile("\\d{3}([ -].*|\r\n)", Pattern.DOTALL);
  private static final Pattern SMTP_MULTI_LINE_RESPONSE =
      Pattern.compile("(?<status>\\d{3})-.+", Pattern.DOTALL);
  private static final Pattern SMTP_SINGLE_LINE_RESPONSE =
      Pattern.compile("(?<status>\\d{3})( (?<body>.+)|)\r\n");

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    buildSmtpResponseFacet(element)
        .ifPresent(
            facet -> {
              element.addFacet(facet);
              element.addFacet(new RbelRootFacet<>(facet));
            });
  }

  private Optional<RbelSmtpResponseFacet> buildSmtpResponseFacet(RbelElement element) {
    return Optional.ofNullable(element.getRawContent())
        .filter(c -> c.length > 4)
        .filter(EmailConversionUtils::endsWithCrLf)
        .map(c -> new String(c, StandardCharsets.UTF_8))
        .filter(s -> SMTP_RESPONSE.matcher(s).matches())
        .flatMap(s -> parseSmtpResponse(element, s));
  }

  private Optional<RbelSmtpResponseFacet> parseSmtpResponse(RbelElement element, String response) {
    String status;
    Optional<String> body;

    var multiResponse = SMTP_MULTI_LINE_RESPONSE.matcher(response);
    if (multiResponse.matches()) {
      status = multiResponse.group("status");
      body = parseMultilineResponse(response, status);
      if (body.isEmpty()) {
        return Optional.empty();
      }
    } else {
      var singleResponse = SMTP_SINGLE_LINE_RESPONSE.matcher(response);
      if (singleResponse.matches()) {
        status = singleResponse.group("status");
        body = Optional.ofNullable(singleResponse.group("body"));
      } else {
        throw new RbelConversionException("unknown response syntax");
      }
    }

    RbelElement bodyElement =
        body.map(bodyString -> RbelElement.wrap(element, bodyString)).orElse(null);

    return Optional.of(
        RbelSmtpResponseFacet.builder()
            .status(RbelElement.wrap(element, status))
            .body(bodyElement)
            .build());
  }

  private static Optional<String> parseMultilineResponse(String response, String status) {
    // multiline responses look like this:
    // <status>-<text><CRLF>
    // ...
    // <status>-<text><CRLF>
    // <status> <text><CRLF>
    StringBuilder body = new StringBuilder();
    var lines = response.split(CRLF);
    var multilinePrefix = status + "-";
    var prefixLength = multilinePrefix.length();
    for (int i = 0; i < lines.length - 1; i++) {
      String line = lines[i];
      if (!line.startsWith(multilinePrefix)) {
        return Optional.empty();
      } else {
        body.append(line.substring(prefixLength));
        body.append(CRLF);
      }
    }
    var lastLine = lines[lines.length - 1].split(" ", 2);
    if (!lastLine[0].equals(status) || lastLine.length != 2) {
      return Optional.empty();
    }
    body.append(lastLine[1]);
    body.append(CRLF);
    return Optional.of(body.toString());
  }
}
