package de.gematik.test.tiger.common.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SourceType {
    YAML(90),
    ENV(80),
    PROPERTIES(70),
    CLI(60),
    RUNTIME_EXPORT(50),
    TEST_CONTEXT(40),
    THREAD_CONTEXT(30);

    private final int precedence;
}
