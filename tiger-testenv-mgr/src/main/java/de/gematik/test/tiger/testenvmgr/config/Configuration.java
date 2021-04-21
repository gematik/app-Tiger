package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class Configuration {

    @JsonProperty
    private List<CfgServer> templates = new ArrayList<>();
    @JsonProperty
    private List<CfgServer> servers = new ArrayList<>();

    @SneakyThrows
    public void readConfig(final URI cfgFile) {
        log.info("reading testenv configuration from " + cfgFile.toURL() + "...");
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()); // jackson databind
        final Configuration cfg = mapper.readValue(cfgFile.toURL().openStream(), Configuration.class);
        servers = cfg.servers;
        templates = cfg.templates;
        log.info("read {} server instances", cfg.getServers().size());
    }

    public void applyTemplates() {
        final Map<String, CfgServer> tmpl = new HashMap<>();
        templates.forEach(t -> tmpl.put(t.getName(), t));
        servers.stream()
            .filter(s -> s.getTemplate() != null && !s.getTemplate().isBlank())
            .forEach(s -> s.merge(tmpl.get(s.getTemplate())));
    }


}
