/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Stream-optimized variant of TigerScopedExecutor. A variable is paired with the executor. Convenience methods allow the merging with other ScopedExecutors.
 */
@RequiredArgsConstructor
public class TigerScopedExecutorWithVariable<T> extends TigerScopedExecutor {

    @Getter
    private final T variable;

    public TigerScopedExecutorWithVariable(T variable, BasicTigerConfigurationSource valueSource) {
        super(valueSource);
        this.variable = variable;
    }

    public TigerScopedExecutorWithVariable(T variable, TigerScopedExecutor scopedExecutor) {
        super(scopedExecutor.scopedValueSource);
        this.variable = variable;
    }

    public void execute(Consumer<T> runnable) {
        TigerGlobalConfiguration.addConfigurationSource(scopedValueSource);
        try {
            runnable.accept(variable);
        } finally {
            TigerGlobalConfiguration.removeConfigurationSource(scopedValueSource);
        }
    }

    public <V> TigerScopedExecutorWithVariable<V> retrieve(Function<T, V> supplier) {
        TigerGlobalConfiguration.addConfigurationSource(scopedValueSource);
        try {
            return new TigerScopedExecutorWithVariable<>(supplier.apply(variable), scopedValueSource);
        } finally {
            TigerGlobalConfiguration.removeConfigurationSource(scopedValueSource);
        }
    }

    public TigerScopedExecutorWithVariable<T> mergeWith(Function<T, TigerScopedExecutor> supplier) {
        TigerGlobalConfiguration.addConfigurationSource(scopedValueSource);
        try {
            final TigerScopedExecutor innerExecutor = supplier.apply(variable);
            innerExecutor.scopedValueSource.getValues().entrySet()
                .forEach(entry -> scopedValueSource.putValue(entry.getKey(), entry.getValue()));
            return this;
        } finally {
            TigerGlobalConfiguration.removeConfigurationSource(scopedValueSource);
        }
    }
}
