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
package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.TigerMasterSecretListeners;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.tls.SecurityParameters;
import org.bouncycastle.tls.TlsContext;

@RequiredArgsConstructor
@Slf4j
public class TigerProxyMasterSecretListener implements TigerMasterSecretListeners {
  private final String masterSecretsFile;

  @Override
  public void onMasterSecret(Object session) {
    if (session instanceof TlsContext ctx) {
      final SecurityParameters securityParametersConnection = ctx.getSecurityParametersConnection();

      log.info("Intercepted master secret, writing to file {}", masterSecretsFile);
      dumpToMasterSecretsFile(
          "CLIENT_RANDOM "
              + HexFormat.of().formatHex(securityParametersConnection.getClientRandom())
              + " "
              + HexFormat.of().formatHex(securityParametersConnection.getMasterSecret().extract())
              + "\n");
    }
  }

  private void dumpToMasterSecretsFile(String line) {
    try {
      Files.write(
          TigerGlobalConfiguration.resolveRelativePathToTigerYaml(masterSecretsFile),
          line.getBytes(),
          StandardOpenOption.APPEND,
          StandardOpenOption.CREATE);
    } catch (Exception e) {
      log.error("Failed to write master secret to file {}", masterSecretsFile, e);
    }
  }
}
