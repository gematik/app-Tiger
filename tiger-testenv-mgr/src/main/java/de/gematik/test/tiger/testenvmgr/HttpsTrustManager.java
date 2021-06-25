/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class HttpsTrustManager implements X509TrustManager {
    private static TrustManager[] trustManagers;
    private static final X509Certificate[] _AcceptedIssuers = new X509Certificate[]{};

    @Override
    public void checkClientTrusted(
        X509Certificate[] x509Certificates, String s)
        throws java.security.cert.CertificateException {

    }

    @Override
    public void checkServerTrusted(
        X509Certificate[] x509Certificates, String s)
        throws java.security.cert.CertificateException {

    }

    public boolean isClientTrusted(X509Certificate[] chain) {
        return true;
    }

    public boolean isServerTrusted(X509Certificate[] chain) {
        return true;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return _AcceptedIssuers;
    }

    private static HostnameVerifier defHostNameVerifier;
    private static SSLSocketFactory defSSLSocketFactory;

    public static void saveContext() {
        defHostNameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        defSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    }

    public static void restoreContext() {
        HttpsURLConnection.setDefaultHostnameVerifier(defHostNameVerifier);
        HttpsURLConnection.setDefaultSSLSocketFactory(defSSLSocketFactory);
    }

    public static void allowAllSSL() {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }

        });

        SSLContext context = null;
        if (trustManagers == null) {
            trustManagers = new TrustManager[]{new HttpsTrustManager()};
        }

        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new TigerTestEnvException("Unable to establish relaxed SSL checks", e);
        }

        HttpsURLConnection.setDefaultSSLSocketFactory(context != null ? context.getSocketFactory() : null);
    }
}
