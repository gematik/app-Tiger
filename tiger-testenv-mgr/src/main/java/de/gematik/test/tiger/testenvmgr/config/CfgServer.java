/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.common.config.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CfgServer extends CfgTemplate {

    private String template;
    @JsonIgnore
    private boolean started = false;
}
