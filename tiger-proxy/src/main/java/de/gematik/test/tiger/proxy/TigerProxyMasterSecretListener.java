package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.TigerMasterSecretListeners;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path.of(masterSecretsFile),
        line.getBytes(),
        StandardOpenOption.APPEND,
        StandardOpenOption.CREATE);
    } catch (Exception e) {
      log.error("Failed to write master secret to file {}", masterSecretsFile, e);
    }
  }
}
