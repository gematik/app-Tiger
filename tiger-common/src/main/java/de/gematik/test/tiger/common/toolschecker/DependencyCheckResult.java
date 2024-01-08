/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.toolschecker;

/**
 * Represents the result of checking if all dependencies are met.
 *
 * @param isValid true if the dependencies are met
 * @param validationMessage information of which dependencies were not met. Useful for logging.
 */
public record DependencyCheckResult(boolean isValid, String validationMessage) {}
