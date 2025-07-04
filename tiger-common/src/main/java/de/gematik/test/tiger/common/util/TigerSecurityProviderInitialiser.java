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

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TIGER_PROXY_DEFAULT_NAMED_GROUPS;

import java.security.Security;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

@Slf4j
public class TigerSecurityProviderInitialiser {

  private static boolean isInitialised = false;

  public static synchronized void initialize() {
    if (!isInitialised) {
      Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX");
      Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
      Security.insertProviderAt(new BouncyCastleProvider(), 1);
      Security.removeProvider(BouncyCastleJsseProvider.PROVIDER_NAME);
      Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
      System.setProperty(
          "jdk.tls.namedGroups",
          String.join(",", TIGER_PROXY_DEFAULT_NAMED_GROUPS.getValueOrDefault()));
      Security.addProvider(new BouncyCastlePQCProvider());
      isInitialised = true;
    }
  }
}
