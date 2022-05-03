/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import javax.validation.constraints.NotNull;
import lombok.NoArgsConstructor;

/**
 * A specialized class that checks for old/deprecated keys.
 */
@NoArgsConstructor
public class DeprecatedKeysForbiddenUsageChecker {

    private static final List<DeprecatedKeyDescriptor> deprecatedKeysMap = List.of(
        DeprecatedKeyDescriptor.builder()
            .compareKey("tiger.servers.*.tigerproxycfg.serverport")
            .deprecatedKey("serverPort")
            .newKey("adminPort")
            .build(),
        DeprecatedKeyDescriptor.builder()
            .compareKey("tiger.tigerproxy.port")
            .deprecatedKey("port")
            .newKey("proxyPort")
            .build(),
        DeprecatedKeyDescriptor.builder()
            .compareKey("tiger.servers.*.tigerproxycfg.proxycfg.port")
            .deprecatedKey("port")
            .newKey("proxyPort")
            .build(),
        DeprecatedKeyDescriptor.builder()
            .compareKey("tiger.servers.*.tigerproxycfg.proxycfg.*")
            .deprecatedKey("proxyCfg")
            .newKey("")
            .build());

    public static void checkForDeprecatedKeys(@NotNull Map<TigerConfigurationKey, String> valueMap)
        throws TigerConfigurationException {
        StringJoiner joiner = new StringJoiner("\n");
        for (DeprecatedKeyDescriptor deprecatedKey : deprecatedKeysMap) {
            valueMap.keySet()
                .stream()
                .filter(key -> key.containsKey(deprecatedKey.getCompareKey()))
                .findFirst()
                .ifPresent(a -> {
                    if (deprecatedKey.getNewKey().length() > 0) {
                        joiner.add("The key ('" + deprecatedKey.getDeprecatedKey()
                            + "') in yaml file should not be used anymore, use '" + deprecatedKey.getNewKey() + "' instead!");
                    } else {
                        joiner.add(
                            "The key ('" + deprecatedKey.getDeprecatedKey() + "') in yaml file should not be used anymore, it is omitted!");
                    }
                });

        }
        if (joiner.toString().length()  > 0) {
            throw new TigerConfigurationException(joiner.toString());
        }
    }
}
