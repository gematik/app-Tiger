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

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.util.CryptoLoader.getCertificateFromPem;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelListFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelX509CertificateFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.Extension;

@Slf4j
@ConverterInfo(onlyActivateFor = "X509")
public class RbelX509Converter extends AbstractX509Converter {

  private static final ZoneId utcZone = ZoneId.of("UTC");

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    if (!tryConversion(element, context, () -> element.getContent().toInputStream())) {
      if (!tryConversion(
          element, context, () -> Base64.getDecoder().wrap(element.getContent().toInputStream()))) {
        tryConversion(
            element,
            context,
            () -> Base64.getUrlDecoder().wrap(element.getContent().toInputStream()));
      }
    }
  }

  private boolean tryConversion(
      RbelElement element, RbelConverter context, Supplier<InputStream> binaryContentExtractor) {
    try {
      val certificate = getCertificateFromPem(binaryContentExtractor.get());

      val x509Facet =
          RbelX509CertificateFacet.builder()
              .serialnumber(certificate.getSerialNumber().toString())
              .issuer(
                  context.convertElement(
                      certificate.getIssuerX500Principal().getEncoded(), element))
              .validFrom(ZonedDateTime.ofInstant(certificate.getNotBefore().toInstant(), utcZone))
              .validUntil(ZonedDateTime.ofInstant(certificate.getNotAfter().toInstant(), utcZone))
              .subject(
                  context.convertElement(
                      certificate.getSubjectX500Principal().getEncoded(), element))
              .parent(element)
              .certificate(certificate)
              .extensions(parseCertificateExtensions(element, context, certificate))
              .build();
      element.addFacet(x509Facet);
      element.addFacet(new RbelRootFacet<>(x509Facet));
      return true;
    } catch (RuntimeException e) {
      // swallow
      return false;
    }
  }

  public RbelElement parseCertificateExtensions(
      RbelElement parent, RbelConverter context, X509Certificate certificate) {
    return RbelListFacet.wrap(
        parent,
        el ->
            streamOfAllExtensions(certificate).map(ex -> parseExtension(ex, el, context)).toList(),
        null);
  }

  private Stream<Extension> streamOfAllExtensions(X509Certificate certificate) {
    return Stream.concat(
        certificate.getCriticalExtensionOIDs().stream()
            .map(oid -> buildExtension(certificate, oid, true)),
        certificate.getNonCriticalExtensionOIDs().stream()
            .map(oid -> buildExtension(certificate, oid, false)));
  }

  private static Extension buildExtension(
      X509Certificate certificate, String oid, boolean critical) {
    try {
      return Extension.create(
          new ASN1ObjectIdentifier(oid),
          critical,
          ASN1Primitive.fromByteArray(certificate.getExtensionValue(oid)));
    } catch (IOException e) {
      throw new RbelConversionException(e);
    }
  }
}
