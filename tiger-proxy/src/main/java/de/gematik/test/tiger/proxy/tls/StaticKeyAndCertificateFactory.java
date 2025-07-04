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
package de.gematik.test.tiger.proxy.tls;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import de.gematik.test.tiger.proxy.exceptions.TigerProxySslException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.security.auth.x500.X500Principal;
import lombok.Builder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class StaticKeyAndCertificateFactory implements KeyAndCertificateFactory {

  static {
    TigerSecurityProviderInitialiser.initialize();
  }

  private final List<TigerPkiIdentity> availableIdentities = new ArrayList<>();

  @Builder
  public StaticKeyAndCertificateFactory(List<TigerPkiIdentity> availableIdentities) {
    if (CollectionUtils.isEmpty(availableIdentities)) {
      throw new TigerProxySslException(
          "No available identities provided in StaticKeyAndCertificateFactory");
    }
    this.availableIdentities.addAll(availableIdentities);
  }

  @Override
  public Optional<TigerPkiIdentity> findExactIdentityForHostname(String hostname) {
    return availableIdentities.stream()
        .filter(id -> matchesHostname(id.getCertificate(), hostname))
        .findAny();
  }

  @Override
  public TigerPkiIdentity resolveIdentityForHostname(String hostname) {
    return findExactIdentityForHostname(hostname).orElseGet(() -> availableIdentities.get(0));
  }

  private boolean matchesHostname(X509Certificate certificate, String hostname) {
    try {
      if (StringUtils.isEmpty(hostname)) {
        return true;
      }
      if (subjectMatches(certificate.getSubjectX500Principal(), hostname)) {
        return true;
      }
      var alternativeNames = certificate.getSubjectAlternativeNames();
      return alternativeNames != null
          && alternativeNames.stream()
              .flatMap(List::stream)
              .map(Object::toString)
              .anyMatch(hostname::equalsIgnoreCase);
    } catch (CertificateParsingException e) {
      return false;
    }
  }

  private boolean subjectMatches(X500Principal subjectX500Principal, String hostname) {
    String dn = subjectX500Principal.getName();
    for (String part : dn.split(",")) {
      if (part.startsWith("CN=")) {
        return part.substring(3).equalsIgnoreCase(hostname);
      }
    }
    return false;
  }
}
