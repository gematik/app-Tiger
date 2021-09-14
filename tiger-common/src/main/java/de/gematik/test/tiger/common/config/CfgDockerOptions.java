package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CfgDockerOptions {
    private List<String> serviceHealthchecks = new ArrayList<>();
    /**
     * whether to start container with unmodified entrypoint, or whether to modify by adding pki and other stuff,
     * rewriting the entrypoint
     */
    private boolean proxied = true;
    /**
     * For docker type to trigger OneShotStartupStrategy
     */
    private boolean oneShot = false;
    /**
     * for docker types allows to overwrite the entrypoint cmd configured with in the container
     */
    private String entryPoint;

    /** used only by docker */
    @JsonIgnore
    private Map<Integer, Integer> ports;
}
