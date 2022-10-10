/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger;

import de.gematik.test.tiger.lib.TigerDirector;
import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.plugin.SerenityReporter;
import io.cucumber.core.resource.ClassLoaders;
import io.cucumber.core.runtime.Runtime;
import io.cucumber.junit.CucumberSerenityRunner;
import io.cucumber.plugin.Plugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.webdriver.Configuration;
import org.junit.runners.model.InitializationError;

/**
 * When started via Intellij the main method is run.
 * When started via maven the constructor gets called for each driver class.
 * Later must have the TigerCucumberListener registered as plugin in the cucumberoptions
 * When using the tiger maven plugin this is taken care of automgically!
 *
 * First one sets it in the code explicitely.
 */
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
        ArrayList<String> argvList = new ArrayList<>(Arrays.asList(argv));
        int index = argvList.indexOf("--tags");
        if (index <= 0) {
            argvList.add("--tags");
            argvList.add("not @Ignore");
        }
        String[] arr = argvList.toArray(new String[argvList.size()]);
        RuntimeOptions runtimeOptions = (new CommandlineOptionsParser(System.out)).parse(arr).build();
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
