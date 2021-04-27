/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import de.gematik.test.tiger.lib.parser.model.Testcase;
import java.io.File;
import java.util.List;
import java.util.Map;

public interface ITestParser {

    void parseDirectory(final File rootDir);

    Map<String, List<Testcase>> getParsedTestcasesPerAfo();

    Map<String, Testcase> getParsedTestcases();

    Map<String, Testcase> getTestcasesWithoutAfo();
}
