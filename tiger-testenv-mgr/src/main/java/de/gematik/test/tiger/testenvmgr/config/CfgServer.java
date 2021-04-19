package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.Getter;

@Getter
public class CfgServer {

    @JsonProperty
    private String name;
    @JsonProperty
    private CfgProductType product;
    @JsonProperty
    private String instanceUri;
    @JsonProperty
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();
    @JsonProperty
    private String pkiFolder;
    @JsonProperty
    private final List<String> urlMappings = new ArrayList<>();
}
