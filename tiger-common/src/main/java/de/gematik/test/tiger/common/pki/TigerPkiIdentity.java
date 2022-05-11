/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.common.pki;

import de.gematik.rbellogger.util.RbelPkiIdentity;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TigerPkiIdentity extends RbelPkiIdentity {

    private final List<X509Certificate> certificateChain = new ArrayList<>();

    public TigerPkiIdentity(X509Certificate certificate, PrivateKey privateKey) {
        super(certificate, privateKey, Optional.empty());
    }

    public TigerPkiIdentity(String fileLoadingInformation) {
        final TigerPkiIdentity identity = TigerPkiIdentityLoader.loadRbelPkiIdentity(fileLoadingInformation);
        setCertificate(identity.getCertificate());
        setPrivateKey(identity.getPrivateKey());
        setKeyId(identity.getKeyId());
        certificateChain.addAll(identity.getCertificateChain());
    }

    public TigerPkiIdentity addCertificateToCertificateChain(X509Certificate newChainCertificate) {
        certificateChain.add(newChainCertificate);
        return this;
    }

    public boolean hasValidChainWithRootCa() {
        if (getCertificate() == null) {
            return false;
        }
        X509Certificate currentPosition = getCertificate();
        for (X509Certificate nextCertificate : getCertificateChain()) {
            if (!currentPosition.getIssuerDN().equals(nextCertificate.getSubjectDN())) {
                return false;
            }
            currentPosition = nextCertificate;
        }

        return currentPosition.getSubjectDN().equals(currentPosition.getIssuerDN());
    }
}
