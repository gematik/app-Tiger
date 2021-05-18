package de.gematik.test.tiger.aurora;

import de.gematik.aurora.client.common.actions.AuroraClientActionFactory;
import de.gematik.aurora.client.common.actions.interfaces.IAuroraResultClientAction;
import de.gematik.aurora.common.entities.TestResultAssignment;
import de.gematik.aurora.common.entities.UserCredentials;
import de.gematik.aurora.common.entities.enums.TestcaseExecutionVerdict;
import de.gematik.aurora.common.exceptions.AbstractAuroraException;
import de.gematik.aurora.common.messages.enums.AuroraAdditionalTestResultParameter;
import de.gematik.aurora.common.messages.enums.AuroraWorkItemField;
import de.gematik.aurora.connector.session.AuroraSessionFactory;
import de.gematik.aurora.connector.session.actions.parameter.FreezeTestResultParameter;
import de.gematik.test.tiger.lib.parser.SerenityTestResultParser;
import de.gematik.test.tiger.lib.parser.model.Result;
import de.gematik.test.tiger.lib.parser.model.TestResult;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "import-testrun")
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

    @Parameter(property = "import-testrun.aurora.comment", defaultValue = "")
    String auroraComment;

    @Parameter(property = "import-testrun.resultfolder", defaultValue = "target/site/serenity")
    String bddRootFolder;

    public void execute() throws MojoExecutionException {
        List<FreezeTestResultParameter> freezeList = getFreezeTestResultParameters();

        getLog().info("Logging in to Aurora...");
        System.setProperty("aurora.profiles.active", auroraProfile);
        // TODO warum in die props?
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
        } catch (AbstractAuroraException e) {
            throw new MojoExecutionException("Failed to log in to Aurora with user " + auroraUser, e);
        }

        getLog().info("Uploading results to Aurora...");
        if (!freezeResults(freezeList)) {
            throw new MojoExecutionException("Failed to persist test results!");
        }

    }

    List<FreezeTestResultParameter> getFreezeTestResultParameters() throws MojoExecutionException {
        getLog().info("Parsing test results...");

        SerenityTestResultParser resultParser = new SerenityTestResultParser();
        Map<String, TestResult> results = new HashMap<>();
        resultParser.parseDirectoryForResults(results, new File(bddRootFolder));

        getLog().info("Creating result list for Aurora...");
        if (auroraComment.isEmpty()) {
            auroraComment = "created " + LocalDateTime.now();
        }
        List<FreezeTestResultParameter> freezeList = new ArrayList<>();
        for (Entry<String, TestResult> result : results.entrySet()) {
            TestResult tr = result.getValue();
            freezeList.add(
                createFreezeParameter(null, tr.getStatus(),
                    auroraProjectId, result.getKey(), auroraTestrunId,
                    String.valueOf(tr.getStartms()), String.valueOf(tr.getEndms()),
                    auroraComment));
        }
        return freezeList;
    }

    FreezeTestResultParameter createFreezeParameter(
        final String fileToFreeze, final Result result, final String projectId,
        final String testcase, final String testrun,
        final String startTimeInMillis, final String endTimeInMillis, final String comment)
        throws MojoExecutionException {
        TestcaseExecutionVerdict verdictEnum = null;
        FreezeTestResultParameter freezeTestResultParameter = null;
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
        try {
            return AuroraClientActionFactory.getInstance()
                .getFreezeTestResultParameterAction(
                    AuroraSessionFactory.createAuroraSessionForCurrentProfile(),
                    freezeList)
                .execute();
        } catch (final AbstractAuroraException exception) {
            throw new MojoExecutionException(String.format(
                "While persisting test results a failure happened, %d results were not persisted!",
                freezeList.size()), exception);
        }
    }
}
