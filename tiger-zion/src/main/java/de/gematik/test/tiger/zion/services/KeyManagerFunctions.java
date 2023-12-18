/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.zion.services;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.key.IdentityBackedRbelKey;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.zion.ZionException;
import jakarta.annotation.PostConstruct;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KeyManagerFunctions {

  private final RbelLogger rbelLogger;
  private final Environment environment;

  @PostConstruct
  public void initJexl() {
    TigerJexlExecutor.registerAdditionalNamespace("keyMgr", this);
    if (environment.getProperty("local.server.port") != null) {
      TigerGlobalConfiguration.putValue("zion.port", environment.getProperty("local.server.port"));
    }
  }

  public String b64Certificate(String name) {
    return rbelLogger
        .getRbelKeyManager()
        .findKeyByName(name)
        .filter(IdentityBackedRbelKey.class::isInstance)
        .map(IdentityBackedRbelKey.class::cast)
        .map(IdentityBackedRbelKey::getCertificate)
        .map(
            cert -> {
              try {
                return Base64.getEncoder().encodeToString(cert.getEncoded());
              } catch (CertificateEncodingException e) {
                throw new ZionException(
                    "Error while encoding certificate for keyId '" + name + "'", e);
              }
            })
        .orElseThrow(
            () ->
                new ZionException(
                    "Unable to find key or matching certificate for keyId '" + name + "'"));
  }
}
