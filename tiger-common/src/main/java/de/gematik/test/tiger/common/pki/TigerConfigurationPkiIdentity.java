/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.pki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(value = {
    "certificate",
    "privateKey",
    "keyId",
    "certificateChain"
})
public class TigerConfigurationPkiIdentity extends TigerPkiIdentity {
    private String fileLoadingInformation;

    public TigerConfigurationPkiIdentity(String fileLoadingInformation) {
        super(fileLoadingInformation);
        this.fileLoadingInformation = fileLoadingInformation;
    }
}
