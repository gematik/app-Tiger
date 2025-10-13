/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class KeystoreAnalyzer {
  public static void main(String[] args) {
    TigerSecurityProviderInitialiser.initialize();

    String basePath = "tiger-common/src/test/resources/";
    String[] keystores = {
      basePath + "customCa.p12",
      basePath + "egk_aut_keystore.jks",
      basePath + "hera.p12",
      basePath + "keystore.bks",
      basePath + "nonDefaultPw.p12",
      basePath + "pwWithSemicolon.p12",
      basePath + "multikey.p12"
    };

    String[] passwords = {"", "00", "123456", "gematik", "changeit", "Semi;colon", "testpass"};

    for (String keystorePath : keystores) {
      System.out.println("\n=== Analyzing: " + keystorePath + " ===");

      File file = new File(keystorePath);
      if (!file.exists()) {
        System.out.println("❌ File does not exist");
        continue;
      }

      // Try different keystore types
      String[] keystoreTypes = {"PKCS12", "JKS", "BKS"};
      boolean opened = false;

      for (String keystoreType : keystoreTypes) {
        if (opened) break;

        for (String password : passwords) {
          try {
            // Use BouncyCastle provider for PKCS12
            if ("PKCS12".equals(keystoreType)) {
              KeyStore keystore = KeyStore.getInstance(keystoreType, "BC");
              try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keystore.load(fis, password.toCharArray());
              }

              System.out.println(
                  "✅ Opened with type: " + keystoreType + ", password: '" + password + "'");
              opened = true;

              // List all aliases
              var aliases = Collections.list(keystore.aliases());
              System.out.println("📋 Found " + aliases.size() + " aliases:");

              for (String alias : aliases) {
                System.out.println("  🔑 " + alias);

                try {
                  if (keystore.isKeyEntry(alias)) {
                    X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
                    java.security.cert.Certificate[] certArray =
                        keystore.getCertificateChain(alias);

                    System.out.println("    Type: Private key entry");
                    if (cert != null) {
                      System.out.println(
                          "    Subject: " + cert.getSubjectX500Principal().getName());
                      System.out.println("    Issuer: " + cert.getIssuerX500Principal().getName());
                      System.out.println("    Valid until: " + cert.getNotAfter());
                    }
                    System.out.println(
                        "    Chain length: " + (certArray != null ? certArray.length : 1));

                  } else if (keystore.isCertificateEntry(alias)) {
                    X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
                    System.out.println("    Type: Certificate entry");
                    if (cert != null) {
                      System.out.println(
                          "    Subject: " + cert.getSubjectX500Principal().getName());
                    }
                  } else {
                    System.out.println("    Type: Unknown entry type");
                  }
                } catch (Exception e) {
                  System.out.println("    ❌ Error reading alias details: " + e.getMessage());
                }
              }

              break; // Successfully opened, no need to try other passwords

            } else {
              KeyStore keystore = KeyStore.getInstance(keystoreType);
              try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keystore.load(fis, password.toCharArray());
              }

              System.out.println(
                  "✅ Opened with type: " + keystoreType + ", password: '" + password + "'");
              opened = true;

              // List all aliases
              var aliases = Collections.list(keystore.aliases());
              System.out.println("���� Found " + aliases.size() + " aliases:");

              for (String alias : aliases) {
                System.out.println("  🔑 " + alias);

                try {
                  if (keystore.isKeyEntry(alias)) {
                    X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
                    java.security.cert.Certificate[] certArray =
                        keystore.getCertificateChain(alias);

                    System.out.println("    Type: Private key entry");
                    if (cert != null) {
                      System.out.println(
                          "    Subject: " + cert.getSubjectX500Principal().getName());
                      System.out.println("    Issuer: " + cert.getIssuerX500Principal().getName());
                      System.out.println("    Valid until: " + cert.getNotAfter());
                    }
                    System.out.println(
                        "    Chain length: " + (certArray != null ? certArray.length : 1));

                  } else if (keystore.isCertificateEntry(alias)) {
                    X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
                    System.out.println("    Type: Certificate entry");
                    if (cert != null) {
                      System.out.println(
                          "    Subject: " + cert.getSubjectX500Principal().getName());
                    }
                  } else {
                    System.out.println("    Type: Unknown entry type");
                  }
                } catch (Exception e) {
                  System.out.println("    ❌ Error reading alias details: " + e.getMessage());
                }
              }

              break; // Successfully opened, no need to try other passwords
            }

          } catch (Exception e) {
            // Try next password/type combination
            continue;
          }
        }
      }

      if (!opened) {
        System.out.println("❌ Failed to open keystore with any combination of type and password");
      }
    }
  }
}
