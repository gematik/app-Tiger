/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.parser;

import de.gematik.test.tiger.parser.model.TestResult;
import java.io.File;
import java.util.Map;

public interface ITestResultParser {

    void parseDirectoryForResults(Map<String, TestResult> results, File rootdir);
}
