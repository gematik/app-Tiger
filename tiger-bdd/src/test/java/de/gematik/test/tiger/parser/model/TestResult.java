/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.parser.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TestResult extends Testcase {

    public static TestResult fromTestcase(final Testcase tc) {
        final TestResult tr = new TestResult();
        tr.setClazz(tc.getClazz());
        tr.setMethod(tc.getMethod());
        tr.setFeatureName(tc.getFeatureName());
        tr.setScenarioName(tc.getScenarioName());
        tr.setPath(tc.getPath());
        tr.status = Result.UNKNOWN;
        return tr;
    }

    private String suite;
    private Result status;
    private String errmessage;
    private String errtype;
    private String errdetails;
    private String errsysout;
    private String errsyserr;
}


