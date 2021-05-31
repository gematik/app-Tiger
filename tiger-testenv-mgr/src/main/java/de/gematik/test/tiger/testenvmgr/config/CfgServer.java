/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import java.util.*;
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
    private String type;
    @JsonProperty
    private List<String>  source= new ArrayList<>();
    @JsonProperty
    private String version;
    @JsonProperty
    private String workingDir;
    @JsonProperty
    private List<String> options = new ArrayList<>();
    @JsonProperty
    private List<String> arguments = new ArrayList<>();
    @JsonProperty
    private Integer startupTimeoutSec;
    @JsonProperty
    private boolean active = true;
    @JsonProperty
    private final List<CfgKey> pkiKeys = new ArrayList<>();
    @JsonProperty(defaultValue = "true")
    private boolean proxied;
    @JsonProperty(defaultValue = "false")
    private boolean oneShot;
    @JsonProperty
    private String entryPoint;
    @JsonProperty
    private final List<String> environment = new ArrayList<>();
    @JsonProperty
    private final List<String> urlMappings = new ArrayList<>();
    @JsonProperty
    private final List<String> exports = new ArrayList<>();
    @JsonProperty
    @JsonIgnore
    private Map<Integer, Integer> ports;

    //NG TODO refactor using java lang reflect
    public void merge(final CfgServer template) {
        Arrays.stream(getClass().getDeclaredFields()).forEach(f -> mergeField(template, f));
    }

    private void mergeField(CfgServer template, java.lang.reflect.Field f) {
        try {
            Object tempObj = f.get(template);
            Object obj = f.get(this);
            if (tempObj instanceof List) {
                if (((List)obj).isEmpty() && ((List)tempObj) != null && !((List)tempObj).isEmpty()) {
                    ((List)obj).addAll((List)tempObj);
                }
            } else if (tempObj instanceof Map) {
                if (((Map)obj).isEmpty() && ((Map)tempObj) != null && !((Map)tempObj).isEmpty()) {
                    ((Map)obj).putAll((Map)tempObj);
                }
            } else {
                if (obj == null && tempObj != null) {
                    f.set(this, tempObj);
                }
            }
        } catch (IllegalAccessException e) {
            throw new TigerTestEnvException("Unable to merge field " + f.getName(), e);
        }
    }
}
