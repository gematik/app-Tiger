/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.adapter.mojos;

import de.gematik.test.tiger.maven.usecases.Logger;
import lombok.experimental.Delegate;
import org.apache.maven.plugin.logging.Log;

public class MavenLogger implements Logger {

    @Delegate
    private final Log log;

    public MavenLogger(final Log log) {
        this.log = log;
    }

}
