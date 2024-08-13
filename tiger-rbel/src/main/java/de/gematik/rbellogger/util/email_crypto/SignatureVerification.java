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

package de.gematik.rbellogger.util.email_crypto;

import static de.gematik.rbellogger.util.CryptoLoader.getCertificateFromPem;

import de.gematik.rbellogger.util.RbelException;
import eu.europa.esig.dss.cades.validation.CAdESSignature;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.simplereport.jaxb.XmlToken;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.executor.ValidationLevel;
import eu.europa.esig.dss.validation.reports.Reports;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.util.CollectionStore;

public class SignatureVerification {

  private static final String OID_KOMLE_RECIPIENT_EMAILS = "1.2.276.0.76.4.173";

  private SignatureVerification() {}

  public static VerificationResult validate(byte[] signedDocumentBytes) throws IOException {
    DSSDocument signedDocument = new InMemoryDocument(signedDocumentBytes);
    SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(signedDocument);
    CAdESSignature signature = (CAdESSignature) validator.getSignatures().get(0);
    CollectionStore<X509CertificateHolder> cs =
        (CollectionStore<X509CertificateHolder>) signature.getCmsSignedData().getCertificates();
    X509CertificateHolder ch = cs.iterator().next();
    validator.setValidationLevel(ValidationLevel.BASIC_SIGNATURES);
    validator.setCertificateVerifier(generateVerifierFromCertificate(ch));
    Reports reports =
        validator.validateDocument(
            SignatureVerificationParameters.SIGNATURE_CONSTRAINTS_PARAMETERS);
    checkSignedKimMessageForRecipientEmails(signature, reports);
    List<AdvancedSignature> signatures = validator.getSignatures();
    AdvancedSignature advancedSignature = signatures.get(0);
    List<DSSDocument> originalDocuments = validator.getOriginalDocuments(advancedSignature.getId());
    DSSDocument original = originalDocuments.get(0);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      original.writeTo(out);
      var originalData = out.toByteArray();
      return new VerificationResult(originalData, reports);
    }
  }

  private static CommonCertificateVerifier generateVerifierFromCertificate(X509CertificateHolder ch)
      throws IOException {
    CommonTrustedCertificateSource trustedCertSource = new CommonTrustedCertificateSource();
    trustedCertSource.addCertificate(new CertificateToken(getCertificateFromPem(ch.getEncoded())));
    CommonCertificateVerifier certificateVerifier = new CommonCertificateVerifier();
    certificateVerifier.setTrustedCertSources(trustedCertSource);
    return certificateVerifier;
  }

  private static void checkSignedKimMessageForRecipientEmails(
      CAdESSignature signature, Reports reports) {
    if (signature
            .getSignerInformation()
            .getSignedAttributes()
            .get(new ASN1ObjectIdentifier(OID_KOMLE_RECIPIENT_EMAILS))
        == null) {
      SimpleReport simpleReport = reports.getSimpleReport();
      if (simpleReport.getSignaturesCount() > 1) {
        throw new RbelException("There are too many signature informations.");
      }

      if (simpleReport.getSignaturesCount() < 1) {
        throw new RbelException("There are too little signature informations.");
      }

      XmlToken simpleReportXmlToken =
          reports.getSimpleReportJaxb().getSignatureOrTimestampOrEvidenceRecord().get(0);
      simpleReportXmlToken.setIndication(Indication.FAILED);
      reports.getSimpleReportJaxb().setValidSignaturesCount(0);
    }
  }
}
