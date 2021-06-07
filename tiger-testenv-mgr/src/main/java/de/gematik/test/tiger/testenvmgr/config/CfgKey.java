/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import lombok.Data;

@Data
public class CfgKey {
    private String id;
    private String pem;
    private String type;
}
