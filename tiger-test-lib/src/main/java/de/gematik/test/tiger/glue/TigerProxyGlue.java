/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.proxy.TigerProxy;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.When;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TigerProxyGlue {

  /**
   * Changes the forwardMutualTlsIdentity of the local TigerProxy to the given value. The parameter
   * uses the TigerPkiIdentity-syntax used also for the YAML-configuration. For more information
   * refer to the user manual, section "Configuring PKI identities in Tiger Proxy’s tls section". Be
   * aware: This method reboots the internal mockserver, leading to a short period in which the
   * local TigerProxy can not forward traffic. (It will still function in a mesh-setup, no traffic
   * will be lost). Before the method returns the mockserver is successfully restarted.
   *
   * @param certificateFile The certificate to use. Use TigerPkiIdentity-syntax (e.g.
   *     "my/file/name.p12;p12password")
   */
  @Wenn("TGR ändere die forwardMutualTlsIdentity des lokalen TigerProxies zu {tigerResolvedString}")
  @When("TGR change the local TigerProxy forwardMutualTlsIdentity to {tigerResolvedString}")
  public void setLocalTigerProxyForwardMutualTlsIdentity(final String certificateFile) {
    changeTlsSettingForLocalTigerProxy(
        certificateFile, (cfg, crt) -> cfg.getTls().setForwardMutualTlsIdentity(crt));
  }

  /**
   * Changes the serverIdentity of the local TigerProxy to the given value. The parameter uses the
   * TigerPkiIdentity-syntax used also for the YAML-configuration. For more information refer to the
   * user manual, section "Configuring PKI identities in Tiger Proxy’s tls section". Be aware: This
   * method reboots the internal mockserver, leading to a short period in which the local TigerProxy
   * can not forward traffic. (It will still function in a mesh-setup, no traffic will be lost).
   * Before the method returns the mockserver is successfully restarted.
   *
   * @param certificateFile The certificate to use. Use TigerPkiIdentity-syntax (e.g.
   *     "my/file/name.p12;p12password")
   */
  @Wenn("TGR ändere die serverIdentity des lokalen TigerProxies zu {tigerResolvedString}")
  @When("TGR change the local TigerProxy serverIdentity to {tigerResolvedString}")
  public void setLocalTigerProxyServerIdentity(final String certificateFile) {
    changeTlsSettingForLocalTigerProxy(
        certificateFile, (cfg, crt) -> cfg.getTls().setServerIdentity(crt));
  }

  /**
   * Changes the rootCa of the local TigerProxy to the given value. The parameter uses the
   * TigerPkiIdentity-syntax used also for the YAML-configuration. For more information refer to the
   * user manual, section "Configuring PKI identities in Tiger Proxy’s tls section". Be aware: This
   * method reboots the internal mockserver, leading to a short period in which the local TigerProxy
   * can not forward traffic. (It will still function in a mesh-setup, no traffic will be lost).
   * Before the method returns the mockserver is successfully restarted.
   *
   * @param certificateFile The certificate to use. Use TigerPkiIdentity-syntax (e.g.
   *     "my/file/name.p12;p12password")
   */
  @Wenn("TGR ändere die rootCa des lokalen TigerProxies zu {tigerResolvedString}")
  @When("TGR change the local TigerProxy rootCa to {tigerResolvedString}")
  public void setLocalTigerProxyRootCa(final String certificateFile) {
    changeTlsSettingForLocalTigerProxy(
        certificateFile, (cfg, crt) -> cfg.getTls().setServerRootCa(crt));
  }

  private static void changeTlsSettingForLocalTigerProxy(
      String certificateFile,
      BiConsumer<TigerProxyConfiguration, TigerConfigurationPkiIdentity> configurationChanger) {
    final TigerProxy localTigerProxy =
        TigerDirector.getTigerTestEnvMgr()
            .getLocalTigerProxyOptional()
            .orElseThrow(
                () ->
                    new TigerProxyGlueException(
                        "Could not change settings for the local TigerProxy: The local TigerProxy"
                            + " is inactive"));
    final TigerConfigurationPkiIdentity newIdentity =
        new TigerConfigurationPkiIdentity(certificateFile);
    configurationChanger.accept(localTigerProxy.getTigerProxyConfiguration(), newIdentity);
    localTigerProxy.restartMockserver();
  }

  private static class TigerProxyGlueException extends RuntimeException {

    public TigerProxyGlueException(String s) {
      super(s);
    }
  }
}
