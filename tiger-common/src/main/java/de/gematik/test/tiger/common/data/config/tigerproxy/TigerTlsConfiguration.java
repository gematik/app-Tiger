/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerproxy;

import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TigerTlsConfiguration {

  private TigerConfigurationPkiIdentity serverRootCa;
  private TigerConfigurationPkiIdentity forwardMutualTlsIdentity;
  private TigerConfigurationPkiIdentity serverIdentity;
  @Builder.Default private String domainName = "localhost";
  @Builder.Default private List<String> alternativeNames = List.of("127.0.0.1", "localhost");
  // localhost will be part of the certificates twice by default. This is done in case someone just
  // sets the url
  // and assumes localhost will still be supported
  private List<String> serverSslSuites;
  private List<String> clientSslSuites;
  private List<String> serverTlsProtocols;
  private List<String> clientSupportedGroups;
  private TigerConfigurationPkiIdentity ocspSignerIdentity;
}
