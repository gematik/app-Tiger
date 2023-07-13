/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.function.Supplier;

/**
 * Holds local configuration. You can add values to it, but the configuration will only be active within the `execute`
 * statement. This class is not intended for threading!
 */
public class TigerScopedExecutor {

    final BasicTigerConfigurationSource scopedValueSource;

    public TigerScopedExecutor(BasicTigerConfigurationSource valueSource) {
        scopedValueSource = valueSource;
    }

    public TigerScopedExecutor() {
        scopedValueSource = new BasicTigerConfigurationSource(SourceType.SCOPE_LOCAL_CONTEXT);
    }

    public TigerScopedExecutor withValue(String key, String value) {
        scopedValueSource.putValue(new TigerConfigurationKey(
                TigerGlobalConfiguration.resolvePlaceholders(key)),
            value);
        return this;
    }

    public void execute(Runnable runnable) {
        TigerGlobalConfiguration.addConfigurationSource(scopedValueSource);
        try {
            runnable.run();
        } finally {
            TigerGlobalConfiguration.removeConfigurationSource(scopedValueSource);
        }
    }

    public <T> T retrieve(Supplier<T> supplier) {
        TigerGlobalConfiguration.addConfigurationSource(scopedValueSource);
        try {
            return supplier.get();
        } finally {
            TigerGlobalConfiguration.removeConfigurationSource(scopedValueSource);
        }
    }
}
