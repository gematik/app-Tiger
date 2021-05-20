/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.defaultprovider.EnvironmentVariableDefaultProvider;
import de.gematik.test.tiger.proxy.configuration.ForwardProxyInfo;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Data
public class TigerProxyStandalone {

    @Parameter(names = {"-r", "--routes"}, converter = RouteConverter.class)
    private List<Pair<String, String>> proxyRoutes;
    @Parameter(names = {"-p", "--proxy"}, converter = RouteConverter.class)
    private String proxy;

    public static void main(String[] args) {
        final TigerProxyStandalone standalone = new TigerProxyStandalone();
        JCommander jc = JCommander.newBuilder()
            .addObject(standalone)
            .defaultProvider(new EnvironmentVariableDefaultProvider())
            .build();
        jc.parse(args);

        final TigerProxyConfiguration configuration = new TigerProxyConfiguration();
        if (StringUtils.isNotEmpty(standalone.proxy) && standalone.proxy.contains(":")) {
            configuration.setForwardToProxy(new ForwardProxyInfo(standalone.proxy.split(":")[0],
                Integer.parseInt(standalone.proxy.split(":")[1])));
        }
        configuration.setKeyFolders(new ArrayList<>(List.of(".")));
        configuration.setProxyRoutes(new HashMap<>());
        configuration.setActivateRbelEndpoint(true);
        if (standalone.proxyRoutes != null) {
            standalone.proxyRoutes
                .forEach(pair -> configuration.getProxyRoutes()
                    .put(pair.getKey(), pair.getValue()));
        }
        TigerProxy proxy = new TigerProxy(configuration);
        log.info("Proxy running on port {}", proxy.getPort());
        log.info("Connect via --proxy 'localhost:{}'", proxy.getPort());
        log.info("\n\nPress Ctrl+C to terminate");
    }

    public static class RouteConverter implements IStringConverter<Pair> {

        public Pair<String, String> convert(String value) {
            if (StringUtils.isEmpty(value)) {
                throw new IllegalArgumentException("Could not route convert '" + value + "'. Example value: "
                    + "'http://not.a.real.server;http://google.com'");
            }
            String[] parts = value.split(";");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Could not route convert '" + value + "'. Example value: "
                    + "'http://not.a.real.server;http://google.com'");
            }
            return Pair.of(parts[0], parts[1]);
        }
    }
}
