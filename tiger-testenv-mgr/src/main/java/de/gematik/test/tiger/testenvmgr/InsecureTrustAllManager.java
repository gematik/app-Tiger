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

    private static final X509Certificate[] NO_ACCEPTED_ISSUERS = new X509Certificate[]{};

    public static void allowAllSSL(URLConnection urlConnection) {
        if (urlConnection instanceof HttpsURLConnection) {
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null,
                    new TrustManager[]{new InsecureTrustAllManager()},
                    new SecureRandom());
                ((HttpsURLConnection) urlConnection).setSSLSocketFactory(context.getSocketFactory());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new TigerTestEnvException("Unable to establish relaxed SSL checks", e);
            }
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
