/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CfgKey {
    @JsonProperty
    private String id;
    @JsonProperty
    private String pem;
    @JsonProperty
    private String type;
}
