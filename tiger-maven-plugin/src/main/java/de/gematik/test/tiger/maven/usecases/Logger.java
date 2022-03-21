/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.usecases;

public interface Logger {

    void info(CharSequence message);

    void debug(CharSequence message);
}
