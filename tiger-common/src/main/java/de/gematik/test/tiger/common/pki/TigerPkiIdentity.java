package de.gematik.test.tiger.common.pki;

import de.gematik.rbellogger.util.RbelPkiIdentity;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public X509Certificate[] certificateChainAsArray() {
        return certificateChain.toArray(new X509Certificate[certificateChain.size()]);
    }
}
