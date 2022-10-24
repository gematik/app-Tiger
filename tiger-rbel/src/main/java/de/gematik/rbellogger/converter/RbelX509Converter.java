/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelX509Facet;
import lombok.extern.slf4j.Slf4j;

import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.function.Function;
import java.util.function.Supplier;

import static de.gematik.rbellogger.util.CryptoLoader.getCertificateFromPem;

@Slf4j
public class RbelX509Converter implements RbelConverterPlugin {
    private static final ZoneId utcZone = ZoneId.of("UTC");

    @Override
    public void consumeElement(final RbelElement element, final RbelConverter context) {
        tryConversion(element, () -> element.getRawContent());
        tryConversion(element, () -> Base64.getDecoder().decode(element.getRawContent()));
    }

    private void tryConversion(RbelElement element, Supplier<byte[]> binaryContentExtractor) {
        try {
            final X509Certificate certificate = getCertificateFromPem(binaryContentExtractor.get());
            element.addFacet(RbelX509Facet.builder()
                .serialnumber(certificate.getSerialNumber().toString())
                .issuer(certificate.getIssuerDN().getName())
                .validFrom(ZonedDateTime.ofInstant(certificate.getNotBefore().toInstant(), utcZone))
                .validUntil(ZonedDateTime.ofInstant(certificate.getNotAfter().toInstant(), utcZone))
                .subject(certificate.getSubjectDN().getName())
                .parent(element)
                .build());
        } catch (RuntimeException e) {
            //swallow
        }
    }
}
