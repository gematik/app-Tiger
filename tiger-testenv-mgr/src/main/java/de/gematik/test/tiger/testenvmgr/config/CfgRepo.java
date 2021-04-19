package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CfgRepo {

    @JsonProperty
    private String name;
    @JsonProperty
    private String url;
    @JsonProperty
    private CfgRepoType type;
}
