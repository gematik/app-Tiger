/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.gematik.test.tiger.lib.parser.model.Result;
import de.gematik.test.tiger.lib.parser.model.TestResult;
import de.gematik.test.tiger.lib.parser.model.Testcase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SerenityTestResultParser implements ITestResultParser {

    @Override
    public void parseDirectoryForResults(final Map<String, TestResult> results, final File rootDir) {
        if (rootDir == null) {
            log.warn("Invalid test source NULL root dir");
        } else {
            final File[] files = rootDir.listFiles();
            if (files == null) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Invalid test source root dir %s", rootDir.getAbsolutePath()));
                }
            } else {
                Arrays.stream(files)
                        .filter(f -> f.isFile() && f.getName().endsWith(".json"))
                        .forEach(f -> inspectFileForResults(f, results));
            }
        }
    }

    // TO DO move to ctor for Testcase with JSONObject as param
    private void inspectFileForResults(final File f, final Map<String, TestResult> results) {
        if (f.getName().equals("requirements.json")) {
            return;
        }
        try {
            final String gherkin = Files.readString(f.toPath());
            final JSONObject jso = new JSONObject(gherkin);
            final TestResult tr = new TestResult();
            setTestCaseClassNMethod(jso, tr);
            if (jso.has("tags")) {
                final JSONArray tags = jso.getJSONArray("tags");
                tr.setPolarionID(IntStream.range(0, tags.length())
                        .mapToObj(i -> tags.getJSONObject(i))
                        .filter(tag -> Objects.equals(tag.getString("type"), "TCID"))
                        .map(tag -> tag.getString("name"))
                        .findAny().orElse(null));
            }

            tr.setStatus(mapSerenityStatus(jso.getString("result")));
            if (tr.getStatus() == Result.ERROR || tr.getStatus() == Result.FAILED) {
                final JSONObject jsoErr;
                if (jso.has("exception")) {
                    jsoErr = jso.getJSONObject("exception");
                } else if (jso.has("testFailureCause")) {
                    jsoErr = jso.getJSONObject("testFailureCause");
                } else {
                    throw new TestParserException("Unable to find failure/error details in " + jso.toString(2));
                }
                if (jsoErr.has("message")) {
                    tr.setErrmessage(jsoErr.getString("message"));
                } else {
                    tr.setErrmessage(jso.getString("testFailureSummary"));
                }
                tr.setErrdetails(""); // TO DO add stacktrace here
                tr.setErrtype(jsoErr.getString("errorType"));
            }
            if (jso.has("startTime")) {
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nXXXXX'['z']'");
                final ZonedDateTime zonedDateTime = ZonedDateTime.parse(jso.getString("startTime"), formatter);
                tr.setStartms(zonedDateTime.toInstant().toEpochMilli());
                tr.setEndms(tr.getStartms() + jso.getInt("duration") * 1000L);
            }

            results.put(tr.getClazz() + ":" + tr.getMethod(), tr);

        } catch (final IOException | JSONException ioe) {
            log.error("Failed to parse BDD file " + f.getAbsolutePath(), ioe);
        }
    }

    private void setTestCaseClassNMethod(final JSONObject jso, final Testcase tc) {
        final String id = jso.getString("id");
        final String[] idarr = id.split(";");
        tc.setClazz(String.join(".", Arrays.copyOf(idarr, idarr.length - 1)));
        tc.setMethod(idarr[idarr.length - 1]);
        tc.setFeatureName(jso.getJSONObject("userStory").getString("storyName"));
        tc.setScenarioName(jso.getString("title"));
        tc.setPath(jso.getJSONObject("userStory").getString("path"));
    }

    private Result mapSerenityStatus(final String serenityStatus) {
        switch (serenityStatus) {
            case "FAILURE":
                return Result.FAILED;
            case "ERROR":
                return Result.ERROR;
            case "SUCCESS":
                return Result.PASSED;
            case "SKIPPED":
                return Result.SKIPPED;
            default:
                return Result.UNKNOWN;
        }
    }
}
