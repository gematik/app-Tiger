/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.cucumber.core.plugin;

import static io.cucumber.core.plugin.TaggedScenario.isIgnored;
import static io.cucumber.core.plugin.TaggedScenario.isManual;
import static io.cucumber.core.plugin.TaggedScenario.isPending;
import static io.cucumber.core.plugin.TaggedScenario.isSkippedOrWIP;
import static io.cucumber.core.plugin.TaggedScenario.manualResultDefinedIn;
import static java.util.stream.Collectors.toList;
import static net.serenitybdd.core.webdriver.configuration.RestartBrowserForEach.FEATURE;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.google.common.collect.Lists;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import io.cucumber.junit.CucumberSerenityBaseRunner;
import io.cucumber.messages.types.Background;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import io.cucumber.messages.types.Tag;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.Plugin;
import io.cucumber.plugin.event.DataTableArgument;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.StepArgument;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestSourceRead;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import io.cucumber.plugin.event.WriteEvent;
import io.cucumber.tagexpressions.Expression;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import lombok.Getter;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.core.SerenityListeners;
import net.serenitybdd.core.SerenityReports;
import net.serenitybdd.core.webdriver.configuration.RestartBrowserForEach;
import net.serenitybdd.cucumber.formatting.ScenarioOutlineDescription;
import net.serenitybdd.cucumber.util.PathUtils;
import net.serenitybdd.cucumber.util.StepDefinitionAnnotationReader;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.model.DataTable;
import net.thucydides.core.model.Rule;
import net.thucydides.core.model.Story;
import net.thucydides.core.model.TakeScreenshots;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.model.TestResult;
import net.thucydides.core.model.TestTag;
import net.thucydides.core.model.screenshots.StepDefinitionAnnotations;
import net.thucydides.core.model.stacktrace.RootCauseAnalyzer;
import net.thucydides.core.reports.ReportService;
import net.thucydides.core.steps.BaseStepListener;
import net.thucydides.core.steps.ExecutedStepDescription;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.core.steps.StepFailure;
import net.thucydides.core.steps.TestSourceType;
import net.thucydides.core.util.Inflector;
import net.thucydides.core.webdriver.Configuration;
import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.internal.AssumptionViolatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tiger Listener based upon SerenityReporter a Cucumber Formatter for Serenity. This is copied from Serenity project till the PR to make
 * Serenity Junit Runner and Serenity Reporter class extendable.
 */
public class TigerCucumberListener implements Plugin, ConcurrentEventListener {

    private static final String OPEN_PARAM_CHAR = "｟";
    private static final String CLOSE_PARAM_CHAR = "｠";

    private static final String SCENARIO_OUTLINE_NOT_KNOWN_YET = "";

    private final Configuration<?> systemConfiguration;

    private final List<BaseStepListener> baseStepListeners;

    private static final String FEATURES_ROOT_PATH = "/features/"; // NOSONAR
    private static final String FEATURES_CLASSPATH_ROOT_PATH = ":features/";
    private final FeatureFileLoader featureLoader = new FeatureFileLoader();

    private List<Tag> scenarioTags;

    private static final Logger LOGGER = LoggerFactory.getLogger(TigerCucumberListener.class);

    private final ThreadLocal<ScenarioContext> localContext = ThreadLocal.withInitial(
        ScenarioContext::new);

    private final Set<URI> contextURISet = new CopyOnWriteArraySet<>();

    ScenarioContext getContext() {
        return localContext.get();
    }

    @Getter
    private final SerenityReporterCallbacks serenityReporterCallbacks = new SerenityReporterCallbacks();

    /**
     * Constructor automatically called by cucumber when class is specified as plugin in @CucumberOptions.
     */
    public TigerCucumberListener() {
        systemConfiguration = Injectors.getInjector().getInstance(Configuration.class);
        baseStepListeners = Collections.synchronizedList(new ArrayList<>());
    }

    public TigerCucumberListener(Configuration<?> systemConfiguration) {
        this.systemConfiguration = systemConfiguration;
        baseStepListeners = Collections.synchronizedList(new ArrayList<>());
    }

    private final FeaturePathFormatter featurePathFormatter = new FeaturePathFormatter();

    private StepEventBus getStepEventBus(URI featurePath) {
        URI prefixedPath = featurePathFormatter.featurePathWithPrefixIfNecessary(featurePath);
        return StepEventBus.eventBusFor(prefixedPath);
    }

    private void setStepEventBus(URI featurePath) {
        URI prefixedPath = featurePathFormatter.featurePathWithPrefixIfNecessary(featurePath);
        StepEventBus.setCurrentBusToEventBusFor(prefixedPath);
    }

    private void initialiseListenersFor(URI featurePath) {
        if (getStepEventBus(featurePath).isBaseStepListenerRegistered()) {
            return;
        }
        SerenityListeners listeners = new SerenityListeners(getStepEventBus(featurePath),
            systemConfiguration);
        baseStepListeners.add(listeners.getBaseStepListener());
    }

    private final EventHandler<TestSourceRead> testSourceReadHandler = this::handleTestSourceRead;
    private final EventHandler<TestCaseStarted> caseStartedHandler = this::handleTestCaseStarted;
    private final EventHandler<TestCaseFinished> caseFinishedHandler = this::handleTestCaseFinished;
    private final EventHandler<TestStepStarted> stepStartedHandler = this::handleTestStepStarted;
    private final EventHandler<TestStepFinished> stepFinishedHandler = this::handleTestStepFinished;
    private final EventHandler<TestRunStarted> runStartedHandler = this::handleTestRunStarted;
    private final EventHandler<TestRunFinished> runFinishedHandler = this::handleTestRunFinished;
    private final EventHandler<WriteEvent> writeEventHandler = this::handleWrite;

    void handleTestRunStarted(TestRunStarted event) {
        serenityReporterCallbacks.handleTestRunStarted(event,
            new ScenarioContextDelegate(getContext()));
    }


    @Override
    public void setEventPublisher(EventPublisher publisher) {
        LOGGER.info("REGISTERING Plugin TigerCucumberListenerNG...");
        publisher.registerHandlerFor(TestSourceRead.class, testSourceReadHandler);
        publisher.registerHandlerFor(TestRunStarted.class, runStartedHandler);
        publisher.registerHandlerFor(TestRunFinished.class, runFinishedHandler);
        publisher.registerHandlerFor(TestCaseStarted.class, caseStartedHandler);
        publisher.registerHandlerFor(TestCaseFinished.class, caseFinishedHandler);
        publisher.registerHandlerFor(TestStepStarted.class, stepStartedHandler);
        publisher.registerHandlerFor(TestStepFinished.class, stepFinishedHandler);
        publisher.registerHandlerFor(WriteEvent.class, writeEventHandler);
    }

    void handleTestSourceRead(TestSourceRead event) {

        serenityReporterCallbacks.handleTestSourceRead(event);

        featureLoader.addTestSourceReadEvent(event);
        URI featurePath = event.getUri();

        featureFrom(featurePath).ifPresent(
            feature -> {
                getContext().setFeatureTags(feature.getTags());

                resetEventBusFor(featurePath);
                initialiseListenersFor(featurePath);
                configureDriver(feature, featurePath);
                Story userStory = userStoryFrom(feature, relativeUriFrom(event.getUri()));
                getStepEventBus(event.getUri()).testSuiteStarted(userStory);
            }
        );
    }

    private void resetEventBusFor(URI featurePath) {
        StepEventBus.clearEventBusFor(featurePath);
    }

    private String relativeUriFrom(URI fullPathUri) {
        boolean useDecodedURI = systemConfiguration.getEnvironmentVariables().getPropertyAsBoolean("use.decoded.url", false);
        String pathURIAsString;
        if (useDecodedURI) {
            pathURIAsString = URLDecoder.decode(fullPathUri.toString(), StandardCharsets.UTF_8);
        } else {
            pathURIAsString = fullPathUri.toString();
        }

        if (pathURIAsString.contains(FEATURES_ROOT_PATH)) {
            return StringUtils.substringAfterLast(pathURIAsString, FEATURES_ROOT_PATH);
        } else {
            return pathURIAsString;
        }
    }

    Optional<Feature> featureFrom(URI featureFileUri) {

        LOGGER.info("Running feature from {}", featureFileUri);
        if (!featureFileUri.toString().contains(FEATURES_ROOT_PATH) && !featureFileUri.toString().contains(FEATURES_CLASSPATH_ROOT_PATH)) {
            LOGGER.warn("Feature from {} is not under the 'features' directory. Requirements report will not be correctly generated!",
                featureFileUri);
        }
        String defaultFeatureId = PathUtils.getAsFile(featureFileUri).getName().replace(".feature", "");
        String defaultFeatureName = Inflector.getInstance().humanize(defaultFeatureId);

        parseGherkinIn(featureFileUri);

        if (isEmpty(featureLoader.getFeatureName(featureFileUri))) {
            return Optional.empty();
        }

        Feature feature = featureLoader.getFeature(featureFileUri);
        if (feature.getName().isEmpty()) {
            feature = featureLoader.featureWithDefaultName(feature, defaultFeatureName);
        }
        return Optional.of(feature);
    }

    private void parseGherkinIn(URI featureFileUri) {
        try {
            featureLoader.getFeature(featureFileUri);
        } catch (Throwable ignoreParsingErrors) {
            LOGGER.warn("Could not parse the Gherkin in feature file {}: file ignored", featureFileUri);
        }
    }

    private Story userStoryFrom(Feature feature, String featureFileUriString) {
        Story userStory = Story.withIdAndPath(TestSourcesModel.convertToId(feature.getName()), feature.getName(), featureFileUriString)
            .asFeature();
        if (!isEmpty(feature.getDescription())) {
            userStory = userStory.withNarrative(feature.getDescription());
        }
        return userStory;
    }

    void handleTestCaseStarted(TestCaseStarted event) {

        URI featurePath = event.getTestCase().getUri();
        getContext().currentFeaturePathIs(featurePath);
        contextURISet.add(featurePath);
        setStepEventBus(featurePath);

        if (FeatureTracker.isNewFeature(event)) {
            // Shut down any drivers remaining open from a previous feature, if @singlebrowser is used.
            // Cucumber has no event to mark the start and end of a feature, so we need to do this here.
            if (RestartBrowserForEach.configuredIn(systemConfiguration.getEnvironmentVariables()).restartBrowserForANew(FEATURE)) {
                ThucydidesWebDriverSupport.closeCurrentDrivers();
            }
            FeatureTracker.startNewFeature(event);
        }

        String scenarioName = event.getTestCase().getName();
        TestSourcesModel.AstNode astNode = featureLoader.getAstNode(getContext().currentFeaturePath(),
            event.getTestCase().getLocation().getLine());

        Optional<Feature> currentFeature = featureFrom(featurePath);

        if ((astNode != null) && currentFeature.isPresent()) {
            getContext().setCurrentScenarioDefinitionFrom(astNode);

            //the sources are read in parallel, global current feature cannot be used
            String scenarioId = scenarioIdFrom(currentFeature.get().getName(),
                TestSourcesModel.convertToId(getContext().currentScenarioDefinition.getName()));
            boolean newScenario = !scenarioId.equals(getContext().getCurrentScenario());
            if (newScenario) {
                configureDriver(currentFeature.get(), getContext().currentFeaturePath());
                if (getContext().isAScenarioOutline()) {
                    getContext().startNewExample();
                    handleExamples(currentFeature.get(),
                        getContext().currentScenarioOutline().getTags(),
                        getContext().currentScenarioOutline().getName(),
                        getContext().currentScenarioOutline().getExamples());
                }
                startOfScenarioLifeCycle(currentFeature.get(), scenarioName, getContext().currentScenarioDefinition,
                    event.getTestCase().getLocation().getLine());
                getContext().currentScenario = scenarioIdFrom(currentFeature.get().getName(),
                    TestSourcesModel.convertToId(getContext().currentScenarioDefinition.getName()));
            } else {
                if (getContext().isAScenarioOutline()) {
                    startExample((long) event.getTestCase().getLocation().getLine(),
                        scenarioName);
                }
            }
            TestSourcesModel.getBackgroundForTestCase(astNode).ifPresent(this::handleBackground);
        }
        io.cucumber.messages.types.Rule rule = getRuleForTestCase(astNode);
        if (rule != null) {
            getContext().stepEventBus().setRule(Rule.from(rule));
        }

        serenityReporterCallbacks.handleTestCaseStarted(event,
            new ScenarioContextDelegate(getContext()));
    }

    private io.cucumber.messages.types.Rule getRuleForTestCase(TestSourcesModel.AstNode astNode) {
        Feature feature = getFeatureForTestCase(astNode);
        Scenario existingScenario = TestSourcesModel.getScenarioDefinition(astNode);
        List<FeatureChild> childrenList = feature.getChildren();
        for (FeatureChild featureChild : childrenList) {
            if (scenarioIsIncludedInARule(existingScenario, featureChild)) {
                return featureChild.getRule().get();
            }
        }
        return null;
    }

    private boolean scenarioIsIncludedInARule(Scenario existingScenario, FeatureChild featureChild) {
        return featureChild.getRule() != null && featureChild.getRule().isPresent()
            && featureChild.getRule().get().getChildren().stream().
            filter(rc -> rc.getScenario().isPresent()).
            map(rc -> rc.getScenario().get()).collect(Collectors.toList()).contains(existingScenario);
    }

    private Feature getFeatureForTestCase(TestSourcesModel.AstNode astNode) {
        while (astNode.parent != null) {
            astNode = astNode.parent;
        }
        return (Feature) astNode.node;
    }

    void handleTestCaseFinished(TestCaseFinished event) {
        serenityReporterCallbacks.handleTestCaseFinished(event,
            new ScenarioContextDelegate(getContext()));

        if (getContext().examplesAreRunning()) {
            handleResult(event.getResult());
            finishExample();
        }

        if (Status.FAILED.equals(event.getResult().getStatus()) && noAnnotatedResultIdDefinedFor(
            event)) {
            getStepEventBus(event.getTestCase().getUri()).testFailed(event.getResult().getError());
        } else {
            getStepEventBus(event.getTestCase().getUri()).testFinished(
                getContext().examplesAreRunning());
        }
        getContext().clearStepQueue();
    }


    private boolean noAnnotatedResultIdDefinedFor(TestCaseFinished event) {
        BaseStepListener baseStepListener = getStepEventBus(event.getTestCase().getUri()).getBaseStepListener();
        return (baseStepListener.getTestOutcomes().isEmpty() || (latestOf(baseStepListener.getTestOutcomes()).getAnnotatedResult()
            == null));
    }

    private TestOutcome latestOf(List<TestOutcome> testOutcomes) {
        return testOutcomes.get(testOutcomes.size() - 1);
    }

    void handleTestStepStarted(TestStepStarted event) {
        StepDefinitionAnnotations.setScreenshotPreferencesTo(
            StepDefinitionAnnotationReader
                .withScreenshotLevel(systemConfiguration.getScreenshotLevel()
                    .orElse(TakeScreenshots.UNDEFINED))
                .forStepDefinition(event.getTestStep().getCodeLocation())
                .getScreenshotPreferences());

        if (!(event.getTestStep() instanceof HookTestStep) && event.getTestStep() instanceof PickleStepTestStep) {
            PickleStepTestStep pickleTestStep = (PickleStepTestStep) event.getTestStep();
            TestSourcesModel.AstNode astNode = featureLoader.getAstNode(getContext().currentFeaturePath(),
                pickleTestStep.getStepLine()); // NOSONAR

            if (astNode != null) {
                io.cucumber.messages.types.Step step = (io.cucumber.messages.types.Step) astNode.node;
                if (!getContext().isAddingScenarioOutlineSteps()) {
                    getContext().queueStep(step);
                    getContext().queueTestStep(event.getTestStep());
                }
                if (getContext().isAScenarioOutline()) {
                    int lineNumber = event.getTestCase().getLocation().getLine();
                    getContext().stepEventBus().updateExampleLineNumber(lineNumber);
                }
                io.cucumber.messages.types.Step currentStep = getContext().getCurrentStep();
                String stepTitle = stepTitleFrom(currentStep, pickleTestStep);
                getContext().stepEventBus()
                    .stepStarted(ExecutedStepDescription.withTitle(stepTitle));
                getContext().stepEventBus().updateCurrentStepTitle(normalized(stepTitle));

            }
        }
        serenityReporterCallbacks.handleTestStepStarted(event,
            new ScenarioContextDelegate(getContext()));
    }

    private void handleWrite(WriteEvent event) {
        getContext().stepEventBus().stepStarted(ExecutedStepDescription.withTitle(event.getText()));
        getContext().stepEventBus().stepFinished();
    }

    void handleTestStepFinished(TestStepFinished event) {
        serenityReporterCallbacks.handleTestStepFinished(event,
            new ScenarioContextDelegate(getContext()));
        if (!(event.getTestStep() instanceof HookTestStep)) {

            handleResult(event.getResult());
            StepDefinitionAnnotations.clear();
        }
    }


    void handleTestRunFinished(TestRunFinished event) {
        generateReports();
        assureTestSuiteFinished();
    }

    private ReportService getReportService() {
        return SerenityReports.getReportService(systemConfiguration);
    }

    private void configureDriver(Feature feature, URI featurePath) {
        getStepEventBus(featurePath).setUniqueSession(systemConfiguration.shouldUseAUniqueBrowser());
        List<String> tags = getTagNamesFrom(feature.getTags());
        String requestedDriver = getDriverFrom(tags);
        String requestedDriverOptions = getDriverOptionsFrom(tags);
        if (isNotEmpty(requestedDriver)) {
            ThucydidesWebDriverSupport.useDefaultDriver(requestedDriver);
            ThucydidesWebDriverSupport.useDriverOptions(requestedDriverOptions);
        }
    }

    private List<String> getTagNamesFrom(List<Tag> tags) {
        List<String> tagNames = new ArrayList<>();
        for (Tag tag : tags) {
            tagNames.add(tag.getName());
        }
        return tagNames;
    }

    private String getDriverFrom(List<String> tags) {
        String requestedDriver = null;
        for (String tag : tags) {
            if (tag.startsWith("@driver:")) {
                requestedDriver = tag.substring(8);
            }
        }
        return requestedDriver;
    }

    private String getDriverOptionsFrom(List<String> tags) {
        String requestedDriver = null;
        for (String tag : tags) {
            if (tag.startsWith("@driver-options:")) {
                requestedDriver = tag.substring(16);
            }
        }
        return requestedDriver;
    }

    private void handleExamples(Feature currentFeature, List<Tag> scenarioOutlineTags, String id, List<Examples> examplesList) {
        LineFilters lineFilters = LineFilters.forCurrentContext();
        String featureName = currentFeature.getName();
        List<Tag> currentFeatureTags = currentFeature.getTags();
        getContext().doneAddingScenarioOutlineSteps();
        initializeExamples();
        for (Examples examples : examplesList) {
            if (examplesAreNotExcludedByTags(examples, scenarioOutlineTags, currentFeatureTags)
                && lineFilters.examplesAreNotExcluded(examples, getContext().currentFeaturePath())) {
                List<TableRow> examplesTableRows = examples
                    .getTableBody()
                    .stream()
                    .filter(tableRow -> lineFilters.tableRowIsNotExcludedBy(tableRow, getContext().currentFeaturePath()))
                    .collect(Collectors.toList());
                List<String> headers = getHeadersFrom(examples.getTableHeader().get());
                List<Map<String, String>> rows = getValuesFrom(examplesTableRows, headers);

                Map<Integer, Long> lineNumbersOfEachRow = new HashMap<>();

                for (int i = 0; i < examplesTableRows.size(); i++) {
                    TableRow tableRow = examplesTableRows.get(i);
                    lineNumbersOfEachRow.put(i, tableRow.getLocation().getLine());
                    addRow(headers, tableRow);
                    if (examples.getTags() != null) {
                        exampleTags().put(examplesTableRows.get(i).getLocation().getLine(), examples.getTags());
                    }
                }
                String scenarioId = scenarioIdFrom(featureName, id);
                boolean newScenario = !getContext().hasScenarioId(scenarioId);

                String exampleTableName = trim(examples.getName());
                String exampleTableDescription = trim(examples.getDescription());
                if (newScenario) {
                    getContext().setTable(
                        dataTableFrom(SCENARIO_OUTLINE_NOT_KNOWN_YET,
                            headers,
                            rows,
                            exampleTableName,
                            exampleTableDescription,
                            lineNumbersOfEachRow));
                } else {
                    getContext().addTableRows(headers,
                        rows,
                        exampleTableName,
                        exampleTableDescription,
                        lineNumbersOfEachRow);
                }
                getContext().addTableTags(tagsIn(examples));

                getContext().setCurrentScenarioId(scenarioId);
            }
        }
    }

    @NotNull
    private List<TestTag> tagsIn(Examples examples) {
        return examples.getTags().stream().map(tag -> TestTag.withValue(tag.getName().substring(1))).collect(Collectors.toList());
    }

    private boolean examplesAreNotExcludedByTags(Examples examples, List<Tag> scenarioOutlineTags, List<Tag> currentFeatureTags) {
        if (testRunHasFilterTags()) {
            return examplesMatchFilter(examples, scenarioOutlineTags, currentFeatureTags);
        }
        return true;
    }

    private boolean examplesMatchFilter(Examples examples, List<Tag> scenarioOutlineTags, List<Tag> currentFeatureTags) {
        List<Tag> allExampleTags = getExampleAllTags(examples, scenarioOutlineTags, currentFeatureTags);
        List<String> allTagsForAnExampleScenario = allExampleTags.stream().map(Tag::getName).collect(Collectors.toList());
        Expression tagValuesFromCucumberOptions = getCucumberRuntimeTags().get(0);
        return tagValuesFromCucumberOptions.evaluate(allTagsForAnExampleScenario);
    }

    private boolean testRunHasFilterTags() {
        List<Expression> tagFilters = getCucumberRuntimeTags();
        return !tagFilters.isEmpty();
    }

    private List<Expression> getCucumberRuntimeTags() {
        if (CucumberSerenityBaseRunner.currentRuntimeOptions() == null) {
            return new ArrayList<>();
        } else {
            return CucumberSerenityBaseRunner.currentRuntimeOptions().getTagExpressions();
        }
    }

    private List<Tag> getExampleAllTags(Examples examples, List<Tag> scenarioOutlineTags, List<Tag> currentFeatureTags) {
        List<Tag> exampleTags = examples.getTags();
        List<Tag> allTags = new ArrayList<>();
        if (exampleTags != null) {
            allTags.addAll(exampleTags);
        }
        if (scenarioOutlineTags != null) {
            allTags.addAll(scenarioOutlineTags);
        }
        if (currentFeatureTags != null) {
            allTags.addAll(currentFeatureTags);
        }
        return allTags;
    }

    private List<String> getHeadersFrom(TableRow headerRow) {
        return headerRow.getCells().stream().map(TableCell::getValue).collect(Collectors.toList());
    }

    private List<Map<String, String>> getValuesFrom(List<TableRow> examplesTableRows, List<String> headers) {

        List<Map<String, String>> rows = new ArrayList<>();
        for (TableRow examplesTableRow : examplesTableRows) {
            Map<String, String> rowValues = new HashMap<>();
            int column = 0;
            List<String> cells = examplesTableRow.getCells().stream().map(TableCell::getValue)
                .collect(Collectors.toList());
            for (String cellValue : cells) {
                String columnName = headers.get(column++);
                rowValues.put(columnName, cellValue);
            }
            rows.add(rowValues);
        }
        return rows;
    }

    private void addRow(List<String> headers,
        TableRow currentTableRow) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int j = 0; j < headers.size(); j++) {
            List<String> cells = currentTableRow.getCells().stream().map(TableCell::getValue).collect(Collectors.toList());
            row.put(headers.get(j), cells.get(j));
        }
        exampleRows().put(currentTableRow.getLocation().getLine(), row);
    }

    private String scenarioIdFrom(String featureId, String scenarioIdOrExampleId) {
        return (featureId != null && scenarioIdOrExampleId != null) ? String.format("%s;%s", featureId, scenarioIdOrExampleId) : "";
    }

    private void initializeExamples() {
        getContext().setExamplesRunning(true);
    }

    private Map<Long, Map<String, String>> exampleRows() {
        if (getContext().exampleRows == null) {
            getContext().exampleRows = Collections.synchronizedMap(new HashMap<>());
        }
        return getContext().exampleRows;
    }

    private Map<Long, List<Tag>> exampleTags() {
        if (getContext().exampleTags == null) {
            getContext().exampleTags = Collections.synchronizedMap(new HashMap<>());
        }
        return getContext().exampleTags;
    }

    private DataTable dataTableFrom(String scenarioOutline,
        List<String> headers,
        List<Map<String, String>> rows,
        String name,
        String description,
        Map<Integer, Long> lineNumbersOfEachRow) {
        return DataTable.withHeaders(headers)
            .andScenarioOutline(scenarioOutline)
            .andMappedRows(rows, lineNumbersOfEachRow)
            .andTitle(name)
            .andDescription(description)
            .build();
    }

    private void startOfScenarioLifeCycle(Feature feature, String scenarioName, Scenario scenario, Integer currentLine) {

        boolean newScenario = !scenarioIdFrom(TestSourcesModel.convertToId(feature.getName()),
            TestSourcesModel.convertToId(scenario.getName())).equals(getContext().currentScenario);
        getContext().currentScenario = scenarioIdFrom(TestSourcesModel.convertToId(feature.getName()),
            TestSourcesModel.convertToId(scenario.getName()));
        if (getContext().examplesAreRunning()) {
            if (newScenario) {
                startScenario(feature, scenario, scenario.getName());
                getContext().stepEventBus().useExamplesFrom(getContext().getTable());
                getContext().stepEventBus().useScenarioOutline(ScenarioOutlineDescription.from(scenario).getDescription());
            } else {
                getContext().stepEventBus().addNewExamplesFrom(getContext().getTable());
            }
            startExample(Long.valueOf(currentLine), scenarioName);
        } else {
            startScenario(feature, scenario, scenarioName);
        }
    }

    private void startScenario(Feature currentFeature, Scenario scenarioDefinition, String scenarioName) {
        getContext().stepEventBus().setTestSource(TestSourceType.TEST_SOURCE_CUCUMBER.getValue());

        getContext().stepEventBus()
            .testStarted(scenarioName,
                scenarioIdFrom(TestSourcesModel.convertToId(currentFeature.getName()),
                    TestSourcesModel.convertToId(scenarioName)));

        getContext().stepEventBus()
            .addDescriptionToCurrentTest(scenarioDefinition.getDescription());
        getContext().stepEventBus()
            .addTagsToCurrentTest(convertCucumberTags(currentFeature.getTags()));
        getContext().stepEventBus()
            .addTagsToCurrentTest(tagsInEnclosingRule(currentFeature, scenarioDefinition));
        if (isScenario(scenarioDefinition) ||
            isScenarioOutline(scenarioDefinition)) {
            getContext().stepEventBus()
                .addTagsToCurrentTest(convertCucumberTags(scenarioDefinition.getTags()));
        }

        registerFeatureJiraIssues(currentFeature.getTags());
        List<Tag> tags = getTagsOfScenarioDefinition(scenarioDefinition);
        registerScenarioJiraIssues(tags);

        scenarioTags = tagsForScenario(scenarioDefinition);
        updateResultFromTags(scenarioTags);
    }

    private List<TestTag> tagsInEnclosingRule(Feature feature, Scenario scenario) {
        List<io.cucumber.messages.types.Rule> nestedRules = feature.getChildren().stream()
            .filter(fc -> fc.getRule().isPresent())
            .map(fc -> fc.getRule().get())
            .collect(toList());

        return nestedRules.stream()
            .filter(rule -> containsScenario(rule, scenario))
            .flatMap(rule -> convertCucumberTags(rule.getTags()).stream())
            .collect(toList());
    }

    private boolean containsScenario(io.cucumber.messages.types.Rule rule, Scenario scenario) {
        return rule.getChildren().stream().anyMatch(child -> child.getScenario().isPresent() && child.getScenario().get() == scenario);
    }

    private List<Tag> tagsForScenario(Scenario scenarioDefinition) {
        List<Tag> tags = new ArrayList<>(getContext().featureTags);
        tags.addAll(getTagsOfScenarioDefinition(scenarioDefinition));
        return tags;
    }


    private boolean isScenario(Scenario scenarioDefinition) {
        return scenarioDefinition.getExamples().isEmpty();
    }

    private boolean isScenarioOutline(Scenario scenarioDefinition) {
        return !isScenario(scenarioDefinition);
    }

    private List<Tag> getTagsOfScenarioDefinition(Scenario scenarioDefinition) {
        if (isScenario(scenarioDefinition) || isScenarioOutline(scenarioDefinition)) {
            return scenarioDefinition.getTags();
        } else {
            return new ArrayList<>();
        }
    }

    private void registerFeatureJiraIssues(List<Tag> tags) {
        List<String> issues = extractJiraIssueTags(tags);
        if (!issues.isEmpty()) {
            getContext().stepEventBus().addIssuesToCurrentStory(issues);
        }
    }

    private void registerScenarioJiraIssues(List<Tag> tags) {
        List<String> issues = extractJiraIssueTags(tags);
        if (!issues.isEmpty()) {
            getContext().stepEventBus().addIssuesToCurrentTest(issues);
        }
    }

    private List<TestTag> convertCucumberTags(List<Tag> cucumberTags) {

        cucumberTags = completeManualTagsIn(cucumberTags);

        return cucumberTags.stream()
            .map(tag -> TestTag.withValue(tag.getName().substring(1)))
            .collect(toList());
    }

    private List<Tag> completeManualTagsIn(List<Tag> cucumberTags) {
        if (unqualifiedManualTag(cucumberTags).isPresent() && doesNotContainResultTag(cucumberTags)) {
            List<Tag> updatedTags = Lists.newArrayList(cucumberTags);

            Tag newManualTag = new Tag(unqualifiedManualTag(cucumberTags).get().getLocation(),
                "@manual:pending",
                UUID.randomUUID().toString());
            updatedTags.add(newManualTag);
            return updatedTags;
        } else {
            return cucumberTags;
        }
    }

    private boolean doesNotContainResultTag(List<Tag> tags) {
        return tags.stream().anyMatch(tag -> tag.getName().startsWith("@manual:"));
    }

    private Optional<Tag> unqualifiedManualTag(List<Tag> tags) {
        return tags.stream().filter(tag -> tag.getName().equalsIgnoreCase("@manual")).findFirst();
    }

    private List<String> extractJiraIssueTags(List<Tag> cucumberTags) {
        List<String> issues = new ArrayList<>();
        for (Tag tag : cucumberTags) {
            if (tag.getName().startsWith("@issue:")) {
                String tagIssueValue = tag.getName().substring("@issue:".length());
                issues.add(tagIssueValue);
            }
            if (tag.getName().startsWith("@issues:")) {
                String tagIssuesValues = tag.getName().substring("@issues:".length());
                issues.addAll(Arrays.asList(tagIssuesValues.split(",")));
            }
        }
        return issues;
    }

    private void startExample(Long lineNumber, String scenarioName) {
        Map<String, String> data = exampleRows().get(lineNumber);
        getContext().stepEventBus().clearStepFailures();
        getContext().stepEventBus().exampleStarted(data, scenarioName);
        if (exampleTags().containsKey(lineNumber)) {
            List<Tag> currentExampleTags = exampleTags().get(lineNumber);
            getContext().stepEventBus().addTagsToCurrentTest(convertCucumberTags(currentExampleTags));
        }
    }

    private void finishExample() {
        getContext().stepEventBus().exampleFinished();
        getContext().exampleCount--;
        if (getContext().exampleCount == 0) {
            getContext().setExamplesRunning(false);
            setTableScenarioOutline();
        } else {
            getContext().setExamplesRunning(true);
        }
    }

    private void setTableScenarioOutline() {
        List<io.cucumber.messages.types.Step> steps = getContext().currentScenarioDefinition.getSteps();
        StringBuilder scenarioOutlineBuffer = new StringBuilder();
        for (io.cucumber.messages.types.Step step : steps) {
            scenarioOutlineBuffer.append(step.getKeyword()).append(step.getText()).append("\n\r");
        }
        String scenarioOutline = scenarioOutlineBuffer.toString();
        if (getContext().getTable() != null) {
            getContext().getTable().setScenarioOutline(scenarioOutline);
        }
    }


    private void handleBackground(Background background) {
        getContext().waitingToProcessBackgroundSteps = true;
        String backgroundName = background.getName();
        if (backgroundName != null) {
            getContext().stepEventBus().setBackgroundTitle(backgroundName);
        }
        String backgroundDescription = background.getDescription();
        if (backgroundDescription == null) {
            backgroundDescription = "";
        }
        getContext().stepEventBus().setBackgroundDescription(backgroundDescription);
    }

    private void assureTestSuiteFinished() {
        getContext().clearStepQueue();
        getContext().clearTestStepQueue();

        contextURISet.forEach(this::cleanupTestResourcesForURI);
        contextURISet.clear();

        Serenity.done();
        getContext().clearTable();
        getContext().setCurrentScenarioId(null);

    }

    private void cleanupTestResourcesForURI(URI uri) {
        getStepEventBus(uri).testSuiteFinished();
        getStepEventBus(uri).dropAllListeners();
        getStepEventBus(uri).clear();
        StepEventBus.clearEventBusFor(uri);
    }

    private void handleResult(Result result) {
        io.cucumber.messages.types.Step currentStep = getContext().nextStep();
        TestStep currentTestStep = getContext().nextTestStep();
        recordStepResult(result, currentStep, currentTestStep);
        if (getContext().noStepsAreQueued()) {
            recordFinalResult();
        }
    }

    private void recordStepResult(Result result, io.cucumber.messages.types.Step currentStep, TestStep currentTestStep) {

        if (getContext().stepEventBus().currentTestIsSuspended()) {
            getContext().stepEventBus().stepIgnored();
        } else if (Status.PASSED.equals(result.getStatus())) {
            getContext().stepEventBus().stepFinished();
        } else if (Status.FAILED.equals(result.getStatus())) {
            failed(stepTitleFrom(currentStep, currentTestStep), result.getError());
        } else if (Status.SKIPPED.equals(result.getStatus())) {
            skipped(stepTitleFrom(currentStep, currentTestStep), result.getError());
        } else if (Status.PENDING.equals(result.getStatus())) {
            getContext().stepEventBus().stepPending();
        } else if (Status.UNDEFINED.equals(result.getStatus())) {
            getContext().stepEventBus().stepPending();
        }
    }

    private void recordFinalResult() {
        if (getContext().waitingToProcessBackgroundSteps) {
            getContext().waitingToProcessBackgroundSteps = false;
        } else {
            updateResultFromTags(scenarioTags);
        }
    }

    private void updateResultFromTags(List<Tag> scenarioTags) {
        if (isManual(scenarioTags)) {
            updateManualResultsFrom(scenarioTags);
        } else if (isPending(scenarioTags)) {
            getContext().stepEventBus().testPending();
        } else if (isSkippedOrWIP(scenarioTags)) {
            getContext().stepEventBus().testSkipped();
            updateCurrentScenarioResultTo(TestResult.SKIPPED);
        } else if (isIgnored(scenarioTags)) {
            getContext().stepEventBus().testIgnored();
            updateCurrentScenarioResultTo(TestResult.IGNORED);
        }
    }

    private void updateManualResultsFrom(List<Tag> scenarioTags) {
        getContext().stepEventBus().testIsManual();

        manualResultDefinedIn(scenarioTags).ifPresent(
            testResult ->
                UpdateManualScenario.forScenario(getContext().currentScenarioDefinition.getDescription())
                    .inContext(getContext().stepEventBus().getBaseStepListener(),
                        systemConfiguration.getEnvironmentVariables())
                    .updateManualScenario(testResult, scenarioTags)
        );
    }

    private void updateCurrentScenarioResultTo(TestResult pending) {
        getContext().stepEventBus().getBaseStepListener().overrideResultTo(pending);
    }

    private void failed(String stepTitle, Throwable cause) {
        if (!errorOrFailureRecordedForStep(stepTitle, cause)) {
            if (!isEmpty(stepTitle)) {
                getContext().stepEventBus().updateCurrentStepTitle(stepTitle);
            }
            Throwable rootCause = new RootCauseAnalyzer(cause).getRootCause().toException();
            if (isAssumptionFailure(rootCause)) {
                getContext().stepEventBus().assumptionViolated(rootCause.getMessage());
            } else {
                getContext().stepEventBus()
                    .stepFailed(new StepFailure(ExecutedStepDescription.withTitle(normalized(currentStepTitle())), rootCause));
            }
        }
    }

    private void skipped(String stepTitle, Throwable cause) {
        if (!errorOrFailureRecordedForStep(stepTitle, cause)) {
            if (!isEmpty(stepTitle)) {
                getContext().stepEventBus().updateCurrentStepTitle(stepTitle);
            }
            if (cause == null) {
                getContext().stepEventBus().stepIgnored();
            } else {
                Throwable rootCause = new RootCauseAnalyzer(cause).getRootCause().toException();
                if (isAssumptionFailure(rootCause)) {
                    getContext().stepEventBus().assumptionViolated(rootCause.getMessage());
                } else {
                    getContext().stepEventBus().stepIgnored();
                }
            }
        }
    }

    private String currentStepTitle() {
        return getContext().stepEventBus().getCurrentStep().stream().map(
            net.thucydides.core.model.TestStep::getDescription
        ).findAny().orElse("");
    }

    private boolean errorOrFailureRecordedForStep(String stepTitle, Throwable cause) {
        if (latestTestOutcome()
            .map(it -> it.testStepWithDescription(stepTitle))
            .isEmpty()) {
            return false;
        }
        Optional<net.thucydides.core.model.TestStep> matchingTestStep = latestTestOutcome().get()
            .testStepWithDescription(stepTitle);
        if (matchingTestStep.isPresent() && matchingTestStep.get().getNestedException() != null) {
            return (matchingTestStep.get().getNestedException().getOriginalCause() == cause);
        }

        return false;
    }

    private Optional<TestOutcome> latestTestOutcome() {

        if (!getContext().stepEventBus().isBaseStepListenerRegistered()) {
            return Optional.empty();
        }

        List<TestOutcome> recordedOutcomes = getContext().stepEventBus().getBaseStepListener().getTestOutcomes();
        return (recordedOutcomes.isEmpty()) ? Optional.empty()
            : Optional.of(recordedOutcomes.get(recordedOutcomes.size() - 1));
    }

    private boolean isAssumptionFailure(Throwable rootCause) {
        return (AssumptionViolatedException.class.isAssignableFrom(rootCause.getClass()));
    }

    private String stepTitleFrom(io.cucumber.messages.types.Step currentStep, TestStep testStep) {
        if (currentStep != null && testStep instanceof PickleStepTestStep) {
            return currentStep.getKeyword()
                + ((PickleStepTestStep) testStep).getStep().getText()
                + embeddedTableDataIn((PickleStepTestStep) testStep);
        }
        return "";
    }

    private String embeddedTableDataIn(PickleStepTestStep currentStep) {
        if (currentStep.getStep().getArgument() != null) {
            StepArgument stepArgument = currentStep.getStep().getArgument();
            if (stepArgument instanceof DataTableArgument) {
                List<Map<String, Object>> rowList = new ArrayList<>();
                for (List<String> row : ((DataTableArgument) stepArgument).cells()) {
                    Map<String, Object> rowMap = new HashMap<>();
                    rowMap.put("cells", row);
                    rowList.add(rowMap);
                }
                return convertToTextTable(rowList);
            }
        }
        return "";
    }


    private String convertToTextTable(List<Map<String, Object>> rows) {
        StringBuilder textTable = new StringBuilder();
        textTable.append(System.lineSeparator());
        for (Map<String, Object> row : rows) {
            textTable.append("|");
            for (String cell : (List<String>) row.get("cells")) {
                textTable.append(" ");
                textTable.append(cell);
                textTable.append(" |");
            }
            if (row != rows.get(rows.size() - 1)) {
                textTable.append(System.lineSeparator());
            }
        }
        return textTable.toString();
    }

    private void generateReports() {
        getReportService().generateReportsFor(getAllTestOutcomes());
    }

    public List<TestOutcome> getAllTestOutcomes() {
        return baseStepListeners.stream().map(BaseStepListener::getTestOutcomes).flatMap(List::stream)
            .collect(Collectors.toList());
    }

    private String normalized(String value) {
        return value.replace(OPEN_PARAM_CHAR, "{").replace(CLOSE_PARAM_CHAR, "}");
    }

    private String trim(String stringToBeTrimmed) {
        return (stringToBeTrimmed == null) ? null : stringToBeTrimmed.trim();
    }
}
