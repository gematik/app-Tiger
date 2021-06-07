package de.gematik.test.tiger.testenvmgr.config;

import java.util.List;
import lombok.Data;

@Data
public class CfgEnvSets {
    private String name;
    private List<String> envVars;
}
