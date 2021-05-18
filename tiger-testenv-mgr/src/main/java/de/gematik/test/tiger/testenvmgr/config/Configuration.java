/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Configuration {

    @JsonProperty
    private List<CfgServer> templates = new ArrayList<>();
    @JsonProperty
    private List<CfgServer> servers = new ArrayList<>();
    @JsonProperty
    private TigerProxyConfiguration tigerProxy;

    @SneakyThrows
    public void readConfig(final URI cfgFile) {
        log.info("reading testenv configuration from " + cfgFile.toURL() + "...");
        final var mapper = new ObjectMapper(new YAMLFactory()); // jackson databind
        final Configuration cfg = mapper.readValue(cfgFile.toURL().openStream(), Configuration.class);
        servers = cfg.servers;
        templates = cfg.templates;
        tigerProxy = cfg.tigerProxy;
        if (cfg.getTemplates().size() > 0) {
            log.info("read {} templates", cfg.getTemplates().size());
        } else {
            log.info("read {} server instances", cfg.getServers().size());
        }
    }

    public void applyTemplates() {
        final Map<String, CfgServer> tmpl = new HashMap<>();
        templates.forEach(t -> tmpl.put(t.getName(), t));
        servers.stream()
            .filter(s -> s.getTemplate() != null && !s.getTemplate().isBlank())
            .forEach(s -> s.merge(tmpl.get(s.getTemplate())));
    }


}
