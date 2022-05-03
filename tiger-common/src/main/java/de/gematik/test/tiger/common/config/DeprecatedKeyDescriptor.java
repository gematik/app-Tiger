/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeprecatedKeyDescriptor {

    private String compareKey;
    private String deprecatedKey;
    private String newKey;
}
