/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CfgServer {

    @JsonProperty
    private String name;
    @JsonProperty
    private String template;
    @JsonProperty
    private CfgProductType product;
    @JsonProperty
    private String instanceUri;
    @JsonProperty
    private List<String> composeFiles = new ArrayList<>();
    @JsonProperty
    private String version;
    @JsonProperty
    private Integer startupTimeoutSec;
    @JsonProperty
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();
    @JsonProperty
    private boolean active = true;
    @JsonProperty
    private final List<CfgKey> pkiKeys = new ArrayList<>();
    @JsonProperty
    private String pkiFolder;
    @JsonProperty(defaultValue="true")
    private boolean proxied;
    @JsonProperty(defaultValue="false")
    private boolean oneShot;
    @JsonProperty
    private String entryPoint;
    @JsonProperty
    private final List<String> exports = new ArrayList<>();
    @JsonProperty
    private final List<String> imports = new ArrayList<>();
    @JsonProperty
    private final List<String> urlMappings = new ArrayList<>();
    @JsonProperty
    @JsonIgnore
    private Map<Integer, Integer> ports;

    public void merge(final CfgServer template) {
        if (product == null && template.product != null) {
            product = template.product;
        }
        if (instanceUri == null && template.instanceUri != null) {
            instanceUri = template.instanceUri;
        }
        if (composeFiles.isEmpty() && template.composeFiles != null && !template.composeFiles.isEmpty()) {
            composeFiles.addAll(template.composeFiles);
        }
        if (version == null && template.version != null) {
            version = template.version;
        }
        if (startupTimeoutSec == null && template.startupTimeoutSec != null) {
            startupTimeoutSec = template.startupTimeoutSec;
        }
        if (params.isEmpty() && template.params != null && !template.params.isEmpty()) {
            params.putAll(template.params);
        }
        if (pkiKeys.isEmpty() && template.pkiKeys != null && !template.pkiKeys.isEmpty()) {
            pkiKeys.addAll(template.pkiKeys);
        }
        if (pkiFolder == null && template.pkiFolder != null) {
            pkiFolder = template.pkiFolder;
        }
        if (entryPoint == null && template.entryPoint != null) {
            entryPoint = template.entryPoint;
        }
        if (exports.isEmpty() && template.exports != null && !template.exports.isEmpty()) {
            exports.addAll(template.exports);
        }
        if (imports.isEmpty() && template.imports != null && !template.imports.isEmpty()) {
            imports.addAll(template.imports);
        }
        if (urlMappings.isEmpty() && template.urlMappings != null && !template.urlMappings.isEmpty()) {
            urlMappings.addAll(template.urlMappings);
        }
    }
}
