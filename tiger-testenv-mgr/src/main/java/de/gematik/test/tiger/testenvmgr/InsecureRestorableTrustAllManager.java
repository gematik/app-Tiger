/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class InsecureRestorableTrustAllManager implements X509TrustManager {
    private static final X509Certificate[] NO_ACCEPTED_ISSUERS = new X509Certificate[]{};
    private static HostnameVerifier defaultHostNameVerifier;
    private static SSLSocketFactory defaultSSLSocketFactory;

    public static void saveContext() {
        defaultHostNameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        defaultSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    }

    public static void restoreContext() {
        HttpsURLConnection.setDefaultHostnameVerifier(defaultHostNameVerifier);
        HttpsURLConnection.setDefaultSSLSocketFactory(defaultSSLSocketFactory);
    }

    public static void allowAllSSL() {
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true); //NOSONAR

        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null,
                    new TrustManager[]{new InsecureRestorableTrustAllManager()},
                    new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(context != null ? context.getSocketFactory() : null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new TigerTestEnvException("Unable to establish relaxed SSL checks", e);
        }

    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) { //NOSONAR
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) { //NOSONAR
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return NO_ACCEPTED_ISSUERS;
    }
}
