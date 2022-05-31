/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger;

import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.plugin.SerenityReporter;
import io.cucumber.core.resource.ClassLoaders;
import io.cucumber.core.runtime.Runtime;
import io.cucumber.junit.CucumberSerenityRunner;
import io.cucumber.plugin.Plugin;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.webdriver.Configuration;
import org.junit.runners.model.InitializationError;

@Slf4j
public class TigerCucumberRunner extends CucumberSerenityRunner {

    private static RuntimeException tigerStartupFailedException;

    public static void main(String[] argv) {
        log.info("Starting TigerCucumberRunner.main()...");
        if (tigerStartupFailedException != null) {
            log.error("Aborting due to earlier errors in setting up Tiger!");
            System.exit(1);
        }
        initializeTiger();
        if (tigerStartupFailedException != null) {
            log.error("Unable to start Tiger!", tigerStartupFailedException);
            System.exit(1);
        }
        Supplier<ClassLoader> classLoaderSupplier = ClassLoaders::getDefaultClassLoader;
        byte exitstatus = run(argv, classLoaderSupplier);
        System.exit(exitstatus);
    }

    public static byte run(String[] argv, Supplier<ClassLoader> classLoaderSupplier) {
        RuntimeOptions runtimeOptions = (new CommandlineOptionsParser(System.out)).parse(argv).build();
        setRuntimeOptions(runtimeOptions);
        Runtime runtime = using(classLoaderSupplier, runtimeOptions);
        runtime.run();
        return runtime.exitStatus();
    }

    public TigerCucumberRunner(Class clazz) throws InitializationError {
        super(clazz);
        log.info("Starting TigerCucumberRunner for {}", clazz.getName());
        if (tigerStartupFailedException != null) {
            throw new InitializationError(tigerStartupFailedException);
        }
        initializeTiger();
        if (tigerStartupFailedException != null) {
            throw new InitializationError(tigerStartupFailedException);
        }
    }

    private synchronized static void initializeTiger() {
        try {
            TigerDirector.registerShutdownHook();
            TigerDirector.start();
        } catch (RuntimeException rte) {
            tigerStartupFailedException = rte;
        }
    }

    public static Runtime using(Supplier<ClassLoader> classLoaderSupplier, RuntimeOptions runtimeOptions) {
        Configuration systemConfiguration = Injectors.getInjector().getInstance(Configuration.class);
        return createSerenityEnabledRuntime(classLoaderSupplier, runtimeOptions, systemConfiguration);
    }

    public static Runtime createSerenityEnabledRuntime(Supplier<ClassLoader> classLoaderSupplier, RuntimeOptions runtimeOptions,
        Configuration systemConfiguration) {
        SerenityReporter reporter = new SerenityReporter(systemConfiguration);
        TigerCucumberListener tigerListener = new TigerCucumberListener();
        return Runtime.builder()
            .withClassLoader(classLoaderSupplier)
            .withRuntimeOptions(runtimeOptions)
            .withAdditionalPlugins(new Plugin[]{reporter, tigerListener})
            .build();
    }
}
