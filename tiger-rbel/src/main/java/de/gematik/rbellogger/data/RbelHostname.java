/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.exceptions.RbelHostnameFormatException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.commons.validator.routines.UrlValidator;

import java.net.URI;
import java.util.Optional;

@Data
@Builder
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
@Slf4j
public class RbelHostname {

    private final String hostname;
    private final int port;

    public static Optional<RbelHostname> fromString(final String value) {
        if (StringUtils.isBlank(value)) {
            return Optional.empty();
        }

        if (value.contains(":")) {
            String[] hostnameValues = value.split(":");
            int port = Integer.parseInt(hostnameValues[1]);

            try {
                return Optional.ofNullable(RbelHostname.builder()
                    .hostname(hostnameValues[0])
                    .port(port)
                    .build());
            } catch (Exception e) {
                throw new RbelHostnameFormatException("Unable to parse hostname: '" + value + "'", e);
            }
        } else {
            return Optional.ofNullable(RbelHostname.builder()
                .hostname(value)
                .build());
        }
    }

    public static Optional<Object> generateFromUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return Optional.empty();
        }

        try {
            final URI uri = new URI(url);
            if (StringUtils.isEmpty(uri.getHost())){
                return Optional.empty();
            }

            if (uri.getPort() > 0) {
                return Optional.of(new RbelHostname(uri.getHost(), uri.getPort()));
            } else if ("http".equals(uri.getScheme())) {
                return Optional.of(new RbelHostname(uri.getHost(), 80));
            } else if ("https".equals(uri.getScheme())) {
                return Optional.of(new RbelHostname(uri.getHost(), 443));
            } else {
                return Optional.of(new RbelHostname(uri.getHost(), 0));
            }
        } catch (Exception e) {
            log.debug("Error while trying to parse URL '{}'", url, e);
            return Optional.empty();
        }
    }

    private static void checkIfUrlIsValid(String url) {
        UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);
        if (!urlValidator.isValid(url)) {
            throw new RbelConversionException(
                "The given URL '" + url + "' is invalid. Please check your configuration.");
        }
    }

    public String toString() {
        if (port > 0) {
            return hostname + ":" + port;
        } else {
            return hostname;
        }
    }
}
