/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;

import de.gematik.test.tiger.lib.parser.model.Result;
import de.gematik.test.tiger.lib.parser.model.TestResult;
import lombok.extern.slf4j.Slf4j;

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
        // TODO workaround for now
        LocalDateTime start = null;
        if (suite.hasAttribute("timestamp")) {
            start = LocalDateTime.parse(suite.getAttribute("timestamp").split(" ")[0],
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }
        final NodeList tcs = suite.getChildNodes();
        for (int i = 0; i < tcs.getLength(); i++) {
            final Node tc = tcs.item(i);
            if (tc.getNodeName().equals("testcase")) {
                final TestResult tr = parseTestCase((Element) tc);
                tr.setSuite(suite.getAttribute("name"));
                if (start != null && !((Element) tc).getAttribute("time").isBlank()) {
                    tr.setStartms(start.toInstant(ZoneOffset.UTC).toEpochMilli());
                    start = start.plus((long) (1000.0 * Float.parseFloat(((Element) tc).getAttribute("time"))), ChronoUnit.MILLIS);
                    tr.setEndms(start.toInstant(ZoneOffset.UTC).toEpochMilli());
                } else {
                    tr.setStartms(0);
                    tr.setEndms(0);
                }
                // TODO workaround for now we assume junit methods have to pass in the polarion ID as test method name
                // Later on parse test case json and look for annotations by matching the test class and method name
                tr.setPolarionID(tr.getMethod());
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
