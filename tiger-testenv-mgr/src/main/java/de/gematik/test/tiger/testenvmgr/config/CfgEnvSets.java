package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class CfgEnvSets {
    @JsonProperty
    private String name;
    @JsonProperty
    private List<String> envVars;
}
