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
