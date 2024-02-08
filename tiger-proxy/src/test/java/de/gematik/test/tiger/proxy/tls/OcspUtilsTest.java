/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.tls;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import lombok.SneakyThrows;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.junit.jupiter.api.Test;

class OcspUtilsTest {
  @SneakyThrows
  @Test
  void checkOcspResponse() {
    final TigerConfigurationPkiIdentity ocspSignerIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/ocspSigner.p12;00");
    final TigerConfigurationPkiIdentity certificate =
        new TigerConfigurationPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");

    byte[] result = OcspUtils.buildOcspResponse(certificate.getCertificate(), ocspSignerIdentity);

    OCSPResp ocspResp = new OCSPResp(result);
    assertThat(ocspResp.getStatus()).isEqualTo(OCSPRespBuilder.SUCCESSFUL);
  }
}
