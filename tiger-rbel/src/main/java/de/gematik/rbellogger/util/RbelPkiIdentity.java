/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RbelPkiIdentity {

    private X509Certificate certificate;
    private PrivateKey privateKey;
    private Optional<String> keyId;
}
