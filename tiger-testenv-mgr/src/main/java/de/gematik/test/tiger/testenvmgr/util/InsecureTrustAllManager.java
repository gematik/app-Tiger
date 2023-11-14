/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.util;

import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class InsecureTrustAllManager implements X509TrustManager {

  private static final X509Certificate[] NO_ACCEPTED_ISSUERS = new X509Certificate[] {};

  public static void allowAllSsl(URLConnection urlConnection) {
    if (urlConnection instanceof HttpsURLConnection) {
      try {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {new InsecureTrustAllManager()}, new SecureRandom());
        ((HttpsURLConnection) urlConnection).setSSLSocketFactory(context.getSocketFactory());
        ((HttpsURLConnection) urlConnection)
            .setHostnameVerifier((hostname, sslSession) -> true); // NOSONAR
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        throw new TigerTestEnvException("Unable to establish relaxed SSL checks", e);
      }
    }
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String s) { // NOSONAR
  }

  @Override
  public void checkServerTrusted(X509Certificate[] x509Certificates, String s) { // NOSONAR
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return NO_ACCEPTED_ISSUERS;
  }
}
