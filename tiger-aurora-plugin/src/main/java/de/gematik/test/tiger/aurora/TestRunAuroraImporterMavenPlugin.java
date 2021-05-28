package de.gematik.test.tiger.aurora;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map.Entry;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import de.gematik.aurora.client.common.actions.AuroraClientActionFactory;
import de.gematik.aurora.client.common.actions.interfaces.IAuroraResultClientAction;
import de.gematik.aurora.common.entities.TestResultAssignment;
import de.gematik.aurora.common.entities.UserCredentials;
import de.gematik.aurora.common.entities.enums.TestcaseExecutionVerdict;
import de.gematik.aurora.common.exceptions.AbstractAuroraException;
import de.gematik.aurora.common.messages.enums.AuroraAdditionalTestResultParameter;
import de.gematik.aurora.common.messages.enums.AuroraWorkItemField;
import de.gematik.aurora.connector.session.AuroraSessionFactory;
import de.gematik.aurora.connector.session.IAuroraSession;
import de.gematik.aurora.connector.session.actions.parameter.FreezeTestResultParameter;
import de.gematik.test.tiger.lib.parser.SerenityTestResultParser;
import de.gematik.test.tiger.lib.parser.model.Result;
import de.gematik.test.tiger.lib.parser.model.TestResult;

@Mojo(name = "import-testrun", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class TestRunAuroraImporterMavenPlugin extends AbstractMojo {

    @Parameter(property = "import-testrun.aurora.profile", defaultValue = "prod_ref_env")
    String auroraProfile;

    @Parameter(property = "import-testrun.aurora.user", defaultValue = "UNSET!")
    String auroraUser;

    @Parameter(property = "import-testrun.aurora.encpassword", defaultValue = "UNSET!")
    String auroraEncPassword;

    @Parameter(property = "import-testrun.aurora.projectId", defaultValue = "UNSET!")
    String auroraProjectId;

    @Parameter(property = "import-testrun.aurora.testrunId", defaultValue = "UNSET!")
    String auroraTestrunId;

    @Parameter(property = "import-testrun.aurora.comment", defaultValue = "no comment")
    String auroraComment;

    @Parameter(property = "import-testrun.resultfolder", defaultValue = "target/site/serenity")
    String bddRootFolder;

    @Parameter(property = "import-testrun.reportfolder", defaultValue = "results")
    String reportFolder;
    @Parameter(property = "import-testrun.reportextension", defaultValue = "hmtl")
    String reportExtension;

    public static void main(final String[] args) {
        final TestRunAuroraImporterMavenPlugin pi = new TestRunAuroraImporterMavenPlugin();
        pi.auroraProfile = "prod_ref_env";
        pi.auroraUser = "t.eitzenberger";
        pi.auroraEncPassword = "SDeSDVveTNleEDS6PNJq7iZ4dj27b1GFdgBv6AWA/HaYs32FebIIrytI8KQIa7gKAT93Dti5f+oEZVyghEVvJLLWhixnxZuxqZuwAsSelQQbNscrcTykG9GxB+YU51yWWZz21PQTgCxZcqwyy1hcpxYTFORhTjp93oBo8tF2MyzNsWdykbEhyArDawy7bNEjrTAE0oPSnLOs0M0w5aipvhyVTlKyn8Kn4lsGkyQ5vcdggIhk6PgrzW0qvmimgaUimPNIamXYFrjObVOkUsuFu8c6dwcVzS4bwSzpDSZrNsQHntR/X8zEkZsKnsRxR6jV/Bk9zPkxUUt/wJGBJBZelA==";
        pi.auroraProjectId = "OPB401";
        pi.auroraTestrunId = "TIGER-DEV-TEST2";
        pi.bddRootFolder = "../testaurora/target/site/serenity";
        pi.auroraComment = "Uploaded " + new Date();
        pi.reportFolder = "../testaurora/results";
        pi.reportExtension = "html";

        try {
            pi.execute();
        } catch (final MojoExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        final List<FreezeTestResultParameter> freezeList = getFreezeTestResultParameters();

        getLog().info("Logging in to Aurora...");
        System.setProperty("aurora.profiles.active", auroraProfile);
        System.setProperty("polarion_username", auroraUser);
        System.setProperty("polarion_password", auroraEncPassword);
        final UserCredentials userCredentials = new UserCredentials(auroraUser, auroraEncPassword);
        final IAuroraResultClientAction<Boolean> action = AuroraClientActionFactory.getInstance()
                .getUserCrendetialsActions()
                .getCheckPolarionCredentials(userCredentials);
        try {
            if (!action.executeAndgetResult()) {
                throw new MojoExecutionException("Failed to log in to Aurora with user " + auroraUser);
            }
        } catch (final AbstractAuroraException e) {
            throw new MojoExecutionException("Failed to log in to Aurora with user " + auroraUser, e);
        }

        getLog().info("Log in successfull...");
        getLog().info("Uploading results [" + freezeList.size() + " to Aurora...");
        if (!freezeResults(freezeList)) {
            throw new MojoExecutionException("Failed to persist test results!");
        }
        getLog().info("Upload trigger call returned successfully!");
    }

    List<FreezeTestResultParameter> getFreezeTestResultParameters() throws MojoExecutionException {
        getLog().info("Parsing test results...");

        final SerenityTestResultParser resultParser = new SerenityTestResultParser();
        final Map<String, TestResult> results = new HashMap<>();
        resultParser.parseDirectoryForResults(results, new File(bddRootFolder));

        getLog().info("Creating result list (" + results.size() + ") for Aurora...");
        if (auroraComment.isEmpty()) {
            auroraComment = "created " + LocalDateTime.now();
        }
        final List<FreezeTestResultParameter> freezeList = new ArrayList<>();
        for (final Entry<String, TestResult> result : results.entrySet()) {
            final TestResult tr = result.getValue();
            if (tr != null) {
                if (tr.getPolarionID() != null) {
                    File resultFile = Paths.get(reportFolder, tr.getPolarionID() + "." + reportExtension).toFile();
                    if (!resultFile.exists()) {
                        resultFile = new File("result." + reportExtension);
                    }
                    freezeList.add(
                            createFreezeParameter(resultFile.getAbsolutePath(), tr.getStatus(),
                                    auroraProjectId, tr.getPolarionID(), auroraTestrunId,
                                    String.valueOf(tr.getStartms()), String.valueOf(tr.getEndms()),
                                    auroraComment));
                } else {
                    getLog().warn("Skipping result for test case " + tr.toString() + " as no TCID annotation was found!");
                }
            }
        }
        return freezeList;
    }

    FreezeTestResultParameter createFreezeParameter(
            final String fileToFreeze, final Result result, final String projectId,
            final String testcase, final String testrun,
            final String startTimeInMillis, final String endTimeInMillis, final String comment)
            throws MojoExecutionException {
        final TestcaseExecutionVerdict verdictEnum;
        final FreezeTestResultParameter freezeTestResultParameter;
        switch (result) {
            case PASSED:
                verdictEnum = TestcaseExecutionVerdict.PASSED;
                break;
            case FAILED:
                verdictEnum = TestcaseExecutionVerdict.FAILED;
                break;
            case ERROR:
                verdictEnum = TestcaseExecutionVerdict.ERROR;
                break;
            case SKIPPED:
                verdictEnum = TestcaseExecutionVerdict.CANCELED;
                break;
            default:
                verdictEnum = TestcaseExecutionVerdict.INCONCLUSIVE;
        }
        File testresultFile = null;
        try {
            if (fileToFreeze != null) {
                testresultFile = new File(fileToFreeze);
                if (!testresultFile.exists()) {
                    throw new MojoExecutionException(
                            String.format("The result file '%s' was not found!", fileToFreeze));
                }
            }

            final TestResultAssignment testResultAssignment = new TestResultAssignment();
            testResultAssignment.setAuroraField(AuroraWorkItemField.PROJECT, projectId)
                    .setAuroraField(AuroraWorkItemField.INTERNAL_ID, testcase)
                    .setAuroraField(AuroraWorkItemField.TESTRUN, testrun);
            testResultAssignment
                    .setAdditionalTestResultParameter(AuroraAdditionalTestResultParameter.AURORA_TESTRESULT_START,
                            startTimeInMillis)
                    .setAdditionalTestResultParameter(AuroraAdditionalTestResultParameter.AURORA_TESTRESULT_END,
                            endTimeInMillis)
                    .setAdditionalTestResultParameter(AuroraAdditionalTestResultParameter.AURORA_TESTRESULT_COMMENT,
                            comment);
            freezeTestResultParameter = new FreezeTestResultParameter(testresultFile, testResultAssignment,
                    verdictEnum);
            return freezeTestResultParameter;
        } catch (final Exception exception) {
            throw new MojoExecutionException(String.format(
                    "Persisting result '%s' of test case '%s', test run '%s', verdict '%s' failed with: %s",
                    testresultFile != null ? testresultFile.getAbsolutePath() : "NO FILE", testcase, testrun,
                    verdictEnum, exception.getMessage()), exception);
        }
    }

    boolean freezeResults(final List<FreezeTestResultParameter> freezeList) throws MojoExecutionException {
        try (final IAuroraSession session = AuroraSessionFactory.createAuroraSessionForCurrentProfile()) {
            return AuroraClientActionFactory.getInstance()
                    .getFreezeTestResultParameterAction(session, freezeList)
                    .execute();
        } catch (final Exception exception) {
            throw new MojoExecutionException(String.format(
                    "While persisting test results a failure happened, %d results were not persisted!",
                    freezeList.size()), exception);
        }
    }
}
