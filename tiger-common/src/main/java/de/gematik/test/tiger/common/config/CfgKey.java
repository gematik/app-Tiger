/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@ToString
@NoArgsConstructor
@Data
@Builder
public class CfgKey {
    private String id;
    private String pem;
    @Builder.Default
    private PkiType type = PkiType.Certificate;
}
