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

package de.gematik.test.tiger.maven.adapter.mojos;

import com.google.code.maven_replacer_plugin.file.FileUtils;
import com.google.code.maven_replacer_plugin.include.FileSelector;
import de.gematik.test.tiger.maven.usecases.DriverGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * This plugin allows to generate JUnit4 driver classes for Serenity testruns dynamically in the generate-test-sources
 * phase. To trigger use the "generate-drivers" goal. For more details please referr to the README.adoc file in the
 * project root.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Mojo(name = "generate-drivers", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES)
public class GenerateDriverMojo extends AbstractMojo {

    /**
     * Skip running this plugin. Default is false.
     */
    @Parameter
    private boolean skip = false;

    /**
     * Optional base directory for each file to replace. Path to base relative feature files from. This feature is
     * useful for multi-module projects. Defaults to ${}basedir}/src/test/resources/features.
     */
    @Parameter(alias = "basedir", defaultValue = "${basedir}/src/test/resources/features")
    private String featuresDir;

    /**
     * Mandatory List of files to include. In Ant format (*\/directory/**.feature)
     */
    @Parameter(required = true, defaultValue = "**/*.feature")
    private List<String> includes = new ArrayList<>();

    /**
     * Optional List of files to exclude. In Ant format (*\/directory/**.feature)
     */
    @Parameter
    private List<String> excludes = new ArrayList<>();

    /**
     * Mandatory list of packages containing glue code or hooks
     */
    @Parameter(defaultValue = "${project.groupId}")
    private List<String> glues = new ArrayList<>();

    /**
     * Optional name of the java package the driver class should be generated in. Default is
     * "de.gematik.test.tiger.serenity.drivers"
     */
    @Parameter(defaultValue = "de.gematik.test.tiger.serenity.drivers")
    private String driverPackage;

    /**
     * Optional name of the driver class. MUST contain the token '${ctr}' which gets replaced on execution with a unique
     * counter incremented for each feature file. Default is "Driver${ctr}"
     */
    @Parameter(defaultValue = "Driver${ctr}IT")
    private String driverClassName;

    /**
     * Optional target directory. Default value is "${project.build.directory}"
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String targetFolder;

    /**
     * Optional path to a custom template file to be used for generating the driver Java source code file. Currently
     * supports the following list of tokens:
     * <p>
     *     <ul>
     *         <li>${ctr} ... counter value that is unique and incremented for each feature file.</li>
     *         <li>${package} ... this is where the package declaration of the driver class will be added to.
     *         Either empty or of the pattern "package xxx.yyy.zzz;" where xxx.yyy.zzz is replaced with the
     *         configured driverPackage configuration property.</li>
     *         <li>${driverClassName} ... name of the driver class (with the ctr token already being replaced
     *         by the counter value).</li>
     *         <li>${feature} ... path to the feature file.</li>
     *         <li>${glues} ... comma separated list of glue/hook packages as specified by the glues configuration property</li>
     *     </ul>
     * </p>
     */
    @Parameter
    private File templateFile;

    /**
     * The current project representation.
     */
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    private final FileUtils fileUtils;
    private final FileSelector fileSelector;

    public GenerateDriverMojo() {
        super();
        fileUtils = new FileUtils();
        fileSelector = new FileSelector();
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping");
            return;
        }
        checkParams();
        try {

            getLog().info("Using base dir: " + featuresDir);
            getLog().info("Using build dir: " + targetFolder);
            getLog().debug("Using glues:" + String.join(", ", glues));

            final List<String> files = fileSelector.listIncludes(featuresDir,
                includes,
                excludes);
            if (files.isEmpty()) {
                throw new MojoExecutionException(
                    "No matching feature file found! Please check your include and exclude values");
            }

            getLog().info("Creating test drivers for " + files.size() + " feature files:");

            final Path outputFolder = Paths.get(targetFolder, "generated-test-sources",
                "tigerbdd");

            new DriverGenerator(glues, driverPackage, outputFolder, driverClassName,
                templateFile == null ? null : templateFile.toPath(),
                new MavenLogger(getLog()))
                .generateDriverForFeatureFiles(
                    files.stream().map(this::addBasedirPrefix)
                        .collect(Collectors.toList()));

            project.addTestCompileSourceRoot(
                Paths.get(targetFolder, "generated-test-sources", "tigerbdd").toAbsolutePath()
                    .toString());
        } catch (final IOException e) {
            throw new MojoExecutionException("File read/write failure!", e);
        }
    }

    private void checkParams() throws MojoExecutionException {
        if (includes.isEmpty()) {
            throw new MojoExecutionException("Includes are mandatory!");
        }
        if (!driverClassName.contains(DriverGenerator.COUNTER_REPLACEMENT_TOKEN)) {
            throw new MojoExecutionException(
                "Driver class name does not contain ${ctr}! "
                    + "So only one driver java file will be generated and always overwritten! "
                    + "Make sure to include the '${ctr}' token to create driver files for each feature file.");
        }
    }

    private String addBasedirPrefix(final String file) {
        if (StringUtils.isBlank(featuresDir)) {
            return file;
        }
        return featuresDir + "/" + file;
    }

}
