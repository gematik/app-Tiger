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

package io.cucumber.junit;

import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.feature.FeatureParser;
import io.cucumber.core.options.*;
import io.cucumber.core.plugin.PluginFactory;
import io.cucumber.core.plugin.Plugins;
import io.cucumber.core.plugin.TigerCucumberListener;
import io.cucumber.core.resource.ClassLoaders;
import io.cucumber.core.runtime.Runtime;
import io.cucumber.core.runtime.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.webdriver.Configuration;
import org.junit.runners.model.InitializationError;

@Slf4j
/**
 * When started via Intellij the main method is run.
 * When started via maven the constructor gets called for each driver class.
 */
public class TigerCucumberRunner extends CucumberSerenityBaseRunner {

    public static void main(String[] argv) {
        log.info("Starting TigerSerenityRunner.main()...");
        Supplier<ClassLoader> classLoaderSupplier = ClassLoaders::getDefaultClassLoader;
        byte exitstatus = run(argv, classLoaderSupplier);
        System.exit(exitstatus);
    }

    public static byte run(String[] argv, Supplier<ClassLoader> classLoaderSupplier) {
        ArrayList<String> argvList = new ArrayList<>(Arrays.asList(argv));
        int index = argvList.indexOf("--tags");
        if (index <= 0) {
            argvList.add("--tags");
            argvList.add("not @Ignore");
        }
        String[] arr = argvList.toArray(new String[argvList.size()]);
        RuntimeOptions cmdLineOptions = (new CommandlineOptionsParser(System.out)).parse(arr).build(); // NOSONAR
        RuntimeOptions runtimeOptions = new CucumberPropertiesParser()
            .parse(CucumberProperties.fromSystemProperties())
            .enablePublishPlugin()
            .build(cmdLineOptions);

        setRuntimeOptions(runtimeOptions);
        Runtime runtime = using(classLoaderSupplier, runtimeOptions);
        runtime.run();
        return runtime.exitStatus();
    }

    public static Runtime using(Supplier<ClassLoader> classLoaderSupplier, RuntimeOptions runtimeOptions) {
        Configuration systemConfiguration = Injectors.getInjector().getInstance(Configuration.class);
        return createTigerSerenityEnabledRuntime(classLoaderSupplier, runtimeOptions, systemConfiguration);
    }

    public static Runtime createTigerSerenityEnabledRuntime(/*ResourceLoader resourceLoader,*/
        Supplier<ClassLoader> classLoaderSupplier,
        RuntimeOptions runtimeOptions,
        Configuration systemConfiguration) {
        RuntimeOptionsBuilder runtimeOptionsBuilder = new RuntimeOptionsBuilder();
        Collection<String> allTagFilters = environmentSpecifiedTags(runtimeOptions.getTagExpressions());
        for (String tagFilter : allTagFilters) {
            runtimeOptionsBuilder.addTagFilter(new LiteralExpression(tagFilter));
        }
        runtimeOptionsBuilder.build(runtimeOptions);
        setRuntimeOptions(runtimeOptions);


        EventBus bus = new TimeServiceEventBus(Clock.systemUTC(), UUID::randomUUID);
        FeatureParser parser = new FeatureParser(bus::generateId);
        FeaturePathFeatureSupplier featureSupplier = new FeaturePathFeatureSupplier(classLoaderSupplier, runtimeOptions, parser);

        // NOSONAR 3.6.1 TigerSerenityReporterPlugin reporter = new TigerSerenityReporterPlugin(systemConfiguration);
        TigerCucumberListener reporter = new TigerCucumberListener(systemConfiguration);

        return Runtime.builder().withClassLoader(classLoaderSupplier).withRuntimeOptions(runtimeOptions).
            withAdditionalPlugins(reporter).
            withEventBus(bus).withFeatureSupplier(featureSupplier).
            build();
    }


    public TigerCucumberRunner(Class clazz) throws InitializationError {
        super(clazz);
        Assertions.assertNoCucumberAnnotatedMethods(clazz);

        RuntimeOptions runtimeOptions = createRuntimeOptions(clazz);
        log.info("Tag filters {}", runtimeOptions.getTagExpressions());
        JUnitOptions junitOptions = createJUnitOptions(clazz);
        initializeBus();
        setRuntimeOptions(runtimeOptions);

        parseFeaturesEarly();

        // Create plugins after feature parsing to avoid the creation of empty files on lexer errors.
        plugins = new Plugins(new PluginFactory(), runtimeOptions);
        ExitStatus exitStatus = new ExitStatus(runtimeOptions);
        plugins.addPlugin(exitStatus);

        ThreadLocalRunnerSupplier runnerSupplier = initializeServices(clazz, runtimeOptions);

        Configuration systemConfiguration = Injectors.getInjector().getInstance(Configuration.class);
        // 3.6.1 TigerSerenityReporterPlugin reporter = new TigerSerenityReporterPlugin(systemConfiguration);
        TigerCucumberListener reporter = new TigerCucumberListener(systemConfiguration);
        plugins.addPlugin(reporter);

        this.context = new CucumberExecutionContext(bus, exitStatus, runnerSupplier);

        createFeatureRunners(features, runtimeOptions, junitOptions);

    }
}