/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import de.gematik.test.tiger.lib.parser.model.Result;
import de.gematik.test.tiger.lib.parser.model.TestResult;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;

@Slf4j
public class JUnitTestResultParser implements ITestResultParser {

    @Override
    public void parseDirectoryForResults(final Map<String, TestResult> results, final File rootDir) {
        if (rootDir == null) {
            log.warn("Invalid NULL test result root dir");
        } else {
            if (rootDir.listFiles() == null) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Invalid test result root dir %s", rootDir.getAbsolutePath()));
                }
            } else {
                Arrays.asList(Objects.requireNonNull(rootDir.listFiles())).forEach(f -> {
                    if (f.getName().startsWith("TEST-") && f.getName().endsWith(".xml")) {
                        parseJunitXMLResult(f, results);
                    }
                });
            }
        }
    }

    private void parseJunitXMLResult(final File file, final Map<String, TestResult> results) {
        try {
            final DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
            df.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            df.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            final Document doc = df.newDocumentBuilder().parse(file);

            final NodeList suites = doc.getElementsByTagName("testsuite");
            for (int i = 0; i < suites.getLength(); i++) {
                parseTestSuite((Element) suites.item(i), results);
            }
        } catch (final Exception e) {
            if (log.isWarnEnabled()) {
                log.warn(String.format("Failure while parsing result file %s", file.getAbsolutePath()), e);
            }
        }
    }

    private void parseTestSuite(final Element suite, final Map<String, TestResult> results) {
        final NodeList tcs = suite.getChildNodes();
        for (int i = 0; i < tcs.getLength(); i++) {
            final Node tc = tcs.item(i);
            if (tc.getNodeName().equals("testcase")) {
                final TestResult tr = parseTestCase((Element) tc);
                tr.setSuite(suite.getAttribute("name"));
                results.put(tr.getClazz() + ":" + tr.getMethod(), tr);
            }
        }
    }

    private TestResult parseTestCase(final Element tc) {
        final TestResult tr = new TestResult();
        tr.setClazz(tc.getAttribute("classname"));
        tr.setMethod(tc.getAttribute("name"));
        final NodeList details = tc.getChildNodes();
        if (details.getLength() == 0) {
            tr.setStatus(Result.PASSED);
        } else {
            for (int i = 0; i < details.getLength(); i++) {
                if (details.item(i) instanceof Text) {
                    continue;
                }
                final Element detail = (Element) details.item(i);
                boolean parseAttr = false;
                switch (detail.getNodeName()) {
                    case "failure":
                        tr.setStatus(Result.FAILED);
                        parseAttr = true;
                        break;
                    case "error":
                        tr.setStatus(Result.ERROR);
                        parseAttr = true;
                        break;
                    case "skipped":
                        tr.setStatus(Result.SKIPPED);
                        parseAttr = true;
                        break;
                    case "system-out":
                        tr.setErrsysout(detail.getTextContent());
                        break;
                    case "system-err":
                        tr.setErrsyserr(detail.getTextContent());
                        break;
                    default:
                        tr.setStatus(Result.UNKNOWN);
                        break;
                }
                if (parseAttr) {
                    tr.setErrmessage(detail.getAttribute("message"));
                    tr.setErrtype(detail.getAttribute("type"));
                    tr.setErrdetails(detail.getTextContent());
                }
            }
        }
        return tr;
    }
}
