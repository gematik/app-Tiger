package de.gematik.test.tiger.common.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CfgExternalJarOptions {
    private String workingDir;
    private List<String> options = new ArrayList<>();
    private List<String> arguments = new ArrayList<>();
    private String healthcheck;
}