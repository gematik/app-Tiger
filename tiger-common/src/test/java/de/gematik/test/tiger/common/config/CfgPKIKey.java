/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import lombok.Data;

@Data
public class CfgPKIKey {
    String id;
    String type;
    String pem;
}
