/*
 * Copyright (c) 2021 gematik GmbH
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
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
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
