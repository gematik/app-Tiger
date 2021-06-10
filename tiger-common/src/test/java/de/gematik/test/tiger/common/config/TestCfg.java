package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class TestCfg {
    @JsonProperty
    List<CfgTemplate> templates;
}
