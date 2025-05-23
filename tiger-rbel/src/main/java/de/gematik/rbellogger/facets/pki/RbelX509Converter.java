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
package de.gematik.rbellogger.facets.pki;

import static de.gematik.rbellogger.util.CryptoLoader.getCertificateFromPem;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
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
import org.bouncycastle.jce.spec.ECNamedCurveSpec;

@Slf4j
@ConverterInfo(onlyActivateFor = "X509")
public class RbelX509Converter extends AbstractX509Converter {

  private static final ZoneId utcZone = ZoneId.of("UTC");

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
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
      RbelElement element,
      RbelConversionExecutor context,
      Supplier<InputStream> binaryContentExtractor) {
    try {
      val certificate = getCertificateFromPem(binaryContentExtractor.get());

      val x509Facet =
          RbelX509CertificateFacet.builder()
              .version(certificate.getVersion())
              .serialnumber(certificate.getSerialNumber())
              .issuer(
                  context.convertElement(
                      certificate.getIssuerX500Principal().getEncoded(), element))
              .validFrom(ZonedDateTime.ofInstant(certificate.getNotBefore().toInstant(), utcZone))
              .validUntil(ZonedDateTime.ofInstant(certificate.getNotAfter().toInstant(), utcZone))
              .subject(
                  context.convertElement(
                      certificate.getSubjectX500Principal().getEncoded(), element))
              .subjectPublicKeyInfo(
                  RbelMapFacet.wrap(
                      element,
                      el -> {
                        final RbelMultiMap<RbelElement> elementMap =
                            new RbelMultiMap<RbelElement>()
                                .with(
                                    "algorithm",
                                    RbelElement.wrap(el, certificate.getPublicKey().getAlgorithm()))
                                .with(
                                    "format",
                                    RbelElement.wrap(el, certificate.getPublicKey().getFormat()))
                                .with(
                                    "encoded",
                                    new RbelElement(certificate.getPublicKey().getEncoded(), el)
                                        .addFacet(new RbelBinaryFacet()));
                        addKeyParameters(certificate.getPublicKey(), elementMap, el);
                        return elementMap;
                      },
                      null))
              .extensions(parseCertificateExtensions(element, context, certificate))
              .signature(buildSignatureInfo(element, certificate))
              .parent(element)
              .certificate(certificate)
              .build();
      element.addFacet(x509Facet);
      element.addFacet(new RbelRootFacet<>(x509Facet));
      return true;
    } catch (RuntimeException e) {
      // swallow
      return false;
    }
  }

  private void addKeyParameters(
      PublicKey publicKey, RbelMultiMap<RbelElement> rbelElementMap, RbelElement parentNode) {
    if (publicKey instanceof ECPublicKey ecPublicKey) {
      if (ecPublicKey.getParams() instanceof ECNamedCurveSpec ecNamedCurveSpec) {
        rbelElementMap.with("curve", RbelElement.wrap(parentNode, ecNamedCurveSpec.getName()));
      } else {
        rbelElementMap.with("curve", RbelElement.wrap(parentNode, "<unknown>"));
      }
    }
    if (publicKey instanceof RSAPublicKey rsaPublicKey) {
      rbelElementMap.with(
          "modulusLength", RbelElement.wrap(parentNode, rsaPublicKey.getModulus().bitLength()));
    }
  }

  private RbelElement buildSignatureInfo(RbelElement element, X509Certificate certificate) {
    return RbelMapFacet.wrap(
        element,
        el -> {
          final RbelElement oid = RbelElement.wrap(el, certificate.getSigAlgOID());
          OidDictionary.buildAndAddAsn1OidFacet(oid, certificate.getSigAlgOID());
          return new RbelMultiMap<RbelElement>()
              .with("algorithm", oid)
              .with(
                  "encoded",
                  new RbelElement(certificate.getSignature(), el).addFacet(new RbelBinaryFacet()));
        },
        certificate.getSignature());
  }

  public RbelElement parseCertificateExtensions(
      RbelElement parent, RbelConversionExecutor context, X509Certificate certificate) {
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
