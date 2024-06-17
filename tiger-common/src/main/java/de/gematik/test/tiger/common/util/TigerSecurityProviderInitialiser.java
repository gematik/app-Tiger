package de.gematik.test.tiger.common.util;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

public class TigerSecurityProviderInitialiser {
  private static boolean isInitialised = false;

  public static synchronized void initialize() {
    if (!isInitialised) {
      Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX");
      Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
      Security.insertProviderAt(new BouncyCastleProvider(), 1);
      Security.removeProvider(BouncyCastleJsseProvider.PROVIDER_NAME);
      Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
      Security.addProvider(new BouncyCastlePQCProvider());
      isInitialised = true;
    }
  }
}
