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

package de.gematik.test.tiger.proxy.tls;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser;
import de.gematik.test.tiger.mockserver.socket.tls.bouncycastle.AbstractKeyAndCertificateFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.Builder;
import org.apache.commons.collections.ListUtils;

public class StaticTigerKeyAndCertificateFactory extends AbstractKeyAndCertificateFactory {

  static {
    TigerSecurityProviderInitialiser.initialize();
  }

  private final TigerPkiIdentity identity;

  @Builder
  public StaticTigerKeyAndCertificateFactory(TigerPkiIdentity eeIdentity) {
    this.identity = eeIdentity;
  }

  @Override
  public X509Certificate certificateAuthorityX509Certificate() {
    if (!identity.getCertificateChain().isEmpty()) {
      return identity.getCertificateChain().get(identity.getCertificateChain().size() - 1);
    }
    return identity
        .getCertificate(); // necessary because of missing null check in NettySslContextFactory
  }

  @Override
  public PrivateKey privateKey() {
    return identity.getPrivateKey();
  }

  @Override
  public X509Certificate x509Certificate() {
    return identity.getCertificate();
  }

  @Override
  public void buildAndSavePrivateKeyAndX509Certificate() {
    // empty
  }

  @Override
  public List<X509Certificate> certificateChain() {
    return ListUtils.sum(List.of(identity.getCertificate()), identity.getCertificateChain());
  }

  @Override
  public boolean certificateNotYetCreated() {
    return false;
  }
}
