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

package de.gematik.test.tiger.common.web;

import de.gematik.test.tiger.common.exceptions.TigerPkiException;
import java.net.Socket;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class InsecureTrustAllManager extends X509ExtendedTrustManager {

  private static final X509Certificate[] NO_ACCEPTED_ISSUERS = new X509Certificate[] {};

  public static void allowAllSsl(URLConnection urlConnection) {
    if (urlConnection instanceof HttpsURLConnection httpsURLConnection) {
      try {
        final SSLContext context = buildContext();
        httpsURLConnection.setSSLSocketFactory(context.getSocketFactory());
        httpsURLConnection.setHostnameVerifier((hostname, sslSession) -> true); // NOSONAR
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        throw new TigerPkiException("Unable to establish relaxed SSL checks", e);
      }
    }
  }

  public static SSLContext buildContext() throws NoSuchAlgorithmException, KeyManagementException {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, new TrustManager[] {new InsecureTrustAllManager()}, new SecureRandom());
    return context;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String s) { // NOSONAR
    // check nothing, because trust all
  }

  @Override
  public void checkServerTrusted(X509Certificate[] x509Certificates, String s) { // NOSONAR
    // check nothing, because trust all
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return NO_ACCEPTED_ISSUERS;
  }

  @Override
  public void checkClientTrusted( // NOSONAR
      X509Certificate[] chain, String authType, Socket socket) {
    // check nothing, because trust all
  }

  @Override
  public void checkServerTrusted( // NOSONAR
      X509Certificate[] chain, String authType, Socket socket) {
    // check nothing, because trust all
  }

  @Override
  public void checkClientTrusted( // NOSONAR
      X509Certificate[] chain, String authType, SSLEngine engine) {
    // check nothing, because trust all
  }

  @Override
  public void checkServerTrusted( // NOSONAR
      X509Certificate[] chain, String authType, SSLEngine engine) {
    // check nothing, because trust all
  }
}
