/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser.model;

import java.util.Objects;
import lombok.Data;

@Data
public class Testcase {

    private String clazz;
    private String method;
    private String featureName;
    private String scenarioName;
    private String path;

    @Override
    public boolean equals(final Object o) {
        if (o instanceof Testcase) {
            return Objects.equals(((Testcase) o).clazz, clazz) && Objects.equals(((Testcase) o).method,
                method);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (clazz + ":" + method).hashCode();
    }
}
