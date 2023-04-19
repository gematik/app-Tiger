/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SourceType {

    DEFAULTS(110),
    MAIN_YAML(105),
    HOST_YAML(100),
    ADDITIONAL_YAML(95),
    TEST_YAML(90),
    ENV(80),
    PROPERTIES(70),
    CLI(60),
    RUNTIME_EXPORT(50),
    TEST_CONTEXT(40),
    THREAD_CONTEXT(30),
    SCOPE_LOCAL_CONTEXT(20);

    private final int precedence;
}
