/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

/**
 * Holds local configuration. You can add values to it, but the configuration will only be active within the `execute`
 * statement. This class is not intended for threading!
 */
public class TigerScopedExecutor {

    private BasicTigerConfigurationSource scopedValueSource
        = new BasicTigerConfigurationSource(SourceType.SCOPE_LOCAL_CONTEXT);

    public TigerScopedExecutor withValue(String key, String value) {
        scopedValueSource.putValue(new TigerConfigurationKey(
                TigerGlobalConfiguration.resolvePlaceholders(key)),
            value);
        return this;
    }

    public void execute(Runnable runnable) {
        TigerGlobalConfiguration.addConfigurationSource(scopedValueSource);
        runnable.run();
        TigerGlobalConfiguration.removeConfigurationSource(scopedValueSource);
    }
}
