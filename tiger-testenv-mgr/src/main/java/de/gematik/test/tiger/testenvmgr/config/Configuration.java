package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class Configuration {

    @JsonProperty
    private List<CfgRepo> repos = new ArrayList<>();
    @JsonProperty
    private List<CfgServer> servers = new ArrayList<>();

    @SneakyThrows
    public void readConfig(final File cfgFile) {
        log.info("reading testenv configuration from " + cfgFile.getAbsolutePath() + "...");
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()); // jackson databind
        final Configuration cfg = mapper.readValue(cfgFile, Configuration.class);
        repos = cfg.repos;
        servers = cfg.servers;
        log.info("read {} repos and {} server instances", cfg.getRepos().size(), cfg.getServers().size());
    }

}
