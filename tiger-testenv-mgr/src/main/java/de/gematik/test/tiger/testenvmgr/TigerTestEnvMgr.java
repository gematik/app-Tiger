package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.*;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    private final Configuration configuration;

    private final DockerMgr dockerManager;

    @SneakyThrows
    public TigerTestEnvMgr() {
        // read configuration from file and templates from classpath resource
        final File cfgFile = new File(Optional.ofNullable(System.getenv("TIGER_TESTENV_CFGFILE"))
            .orElse(System.getProperty("TIGER_TESTENV_CFGFILE", "tiger-testenv.yaml")));
        configuration = new Configuration();
        configuration.readConfig(cfgFile.toURI());
        final Configuration templates = new Configuration();
        templates.readConfig(Objects.requireNonNull(getClass().getResource("/templates.yaml")).toURI());

        // apply templates to read in configuration
        log.info("applying server templates");
        configuration.getTemplates().addAll(templates.getTemplates());
        configuration.applyTemplates();

        dockerManager = new DockerMgr();
    }

    @Override
    public void setUpEnvironment() {
        log.info("starting set up of test environment...");
        configuration.getServers().forEach(this::start);
        log.info("finished set up test environment OK");
    }

    @Override
    public void composeEnvironment(final File composeFile) {
        // TODO
    }

    @Override
    public List<CfgServer> getTestEnvironment() {
        return configuration.getServers();
    }

    @Override
    public void start(final CfgServer server) {
        final String[] uri = server.getInstanceUri().split(":");
        if (server.isActive()) {
            if (uri[0].equals("docker")) {
                startDocker(server);
            } else if (uri[0].equals("external")) {
                initializeExternal(server);
            } else {
                throw new TigerTestEnvException(
                    String.format("Unsupported server type %s found in server %s", uri[0], server.getName()));
            }
            // set system properties  from exports section
            // TODO and maybe system env in memory, see below
            server.getExports().forEach(exp -> {
                final int sep = exp.indexOf("=");
                assertThat(sep).isNotEqualTo(-1);
                final String key = exp.substring(0, sep);
                String value = exp.substring(sep + 1);
                // ports substitution are only supported for docker based instances
                if (uri[0].equals("docker") && server.getPorts() != null) {
                    for (final Entry entry : server.getPorts().entrySet()) {
                        value = value.replace("${PORT:" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                    }
                }
                value = value.replace("${NAME}", server.getName());
                log.info("  setting system property " + key + "=" + value);
                System.setProperty(key, value);
            });
        } else {
            log.warn("skipping inactive server " + server.getName());
        }
    }

    // dirty hack to make env editable IN MEMORY
    protected static void setEnv(final Map<String, String> newenv) throws Exception {
        try {
            final Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            final Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            final Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            final Field theCaseInsensitiveEnvironmentField = processEnvironmentClass
                .getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            final Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (final NoSuchFieldException e) {
            final Class[] classes = Collections.class.getDeclaredClasses();
            final Map<String, String> env = System.getenv();
            for (final Class cl : classes) {
                if ("java.util.Collections$UnmodifiableMap" .equals(cl.getName())) {
                    final Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    final Object obj = field.get(env);
                    final Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
        }
    }

    private void startDocker(final CfgServer srv) {
        log.info("starting docker instance " + srv.getName() + "...");
        // TODO pass in all exports from all already started servers
        dockerManager.startContainer(srv);
        loadPKIForServer(srv);
        configureProxyForServer(srv);
    }

    public void initializeExternal(final CfgServer srv) {
        log.info("starting external instance " + srv.getName() + "...");
        // TODO SOMEHOW forward all exports from all already started servers
        loadPKIForServer(srv);
        configureProxyForServer(srv);
        log.info("  Checking external instance  " + srv.getName() + " is available ...");
        // TODO check availability
    }

    @Override
    public void shutDown(final CfgServer server) {
        final String[] uri = server.getInstanceUri().split(":");
        // uri[0].equals("docker") nothing to do as testcontainers is cleaning up after run automatically (see ryuk)
        if (uri[0].equals("external")) {
            shutDownExternal(server);
        }
    }

    private void configureProxyForServer(final CfgServer srv) {
        log.info("  initializing proxy for instance " + srv.getName() + "...");
        // TODO send url mappings and keys
    }

    private void loadPKIForServer(final CfgServer srv) {
        log.info("  loading PKI resources for instance " + srv.getName() + "...");
        // TODO initialize pki cert and key pool either from local folder
        //  if env TIGER_TESTENV_PKIFOLDER is set
        // or from classpath in resources/pki
    }

    private void shutDownDocker(final CfgServer server) {
        dockerManager.stopContainer(server);
    }

    private void shutDownExternal(final CfgServer srv) {
        // TODO tell proxy to drop this route
    }

    @SneakyThrows
    private Integer getFreePort() {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
