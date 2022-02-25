/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.driverGenerator;

import com.google.code.maven_replacer_plugin.file.FileUtils;
import com.google.code.maven_replacer_plugin.include.FileSelector;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * This plugin allows to generate JUnit4 driver classes for Serenity testruns dynamically in the generate-test-sources phase.
 * To trigger use the "generate-drivers" goal. For more details please referr to the README.md file in the project root.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Mojo(name = "generate-drivers", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES)
public class SerenityDriverGeneratorMavenPlugin extends AbstractMojo {

    /**
     * Skip running this plugin. Default is false.
     */
    @Parameter
    private boolean skip = false;

    /**
     * Optional base directory for each file to replace. Path to base relative feature files from. This feature is
     * useful for multi-module projects. Default "." which is the default Maven basedir.
     */
    @Parameter
    private String basedir = "";

    /**
     * Mandatory List of files to include. In Ant format (*\/directory/**.feature)
     */
    @Parameter(required = true)
    private List<String> includes = new ArrayList<>();

    /**
     * Optional List of files to exclude. In Ant format (*\/directory/**.feature)
     */
    @Parameter
    private List<String> excludes = new ArrayList<>();

    /**
     * Mandatory list of packages containing glue code or hooks
     */
    @Parameter(required = true)
    private List<String> glues = new ArrayList<>();

    /**
     * Optional name of the java package the driver class should be generated in. Default is
     * "de.gematik.test.tiger.serenity.drivers"
     */
    @Parameter(defaultValue = "de.gematik.test.tiger.serenity.drivers")
    private String driverPackage;

    /**
     * Optional name of the driver class. MUST contain the token '${ctr}' which gets replaced on execution with a unique
     * counter increented for each feature file. Default is "Junit4SerenityTestDriver${ctr}"
     */
    @Parameter(defaultValue = "Junit4SerenityTestDriver${ctr}")
    private String driverClassName;

    /**
     * Optional target directory. Default value is "${project.build.directory}"
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected String targetFolder;

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
    protected String templateFile;

    /**
     * The current project representation.
     */
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    private final FileUtils fileUtils;
    private final FileSelector fileSelector;

    public SerenityDriverGeneratorMavenPlugin() {
        super();
        this.fileUtils = new FileUtils();
        this.fileSelector = new FileSelector();
    }

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping");
            return;
        }
        checkParams();
        try {
            final String template = getTemplate();
            final String packageLine = StringUtils.isBlank(driverPackage) ? "" : "package " + driverPackage + ";\n";
            int ctr = 1;

            getLog().info("Using base dir: " + basedir);
            getLog().info("Using build dir: " + targetFolder);
            String glueCsv = glues.stream().map(g -> "\"" + g + "\"").collect(Collectors.joining(", "));
            getLog().debug("Using glues:" + String.join(", ", glues));

            List<String> files = fileSelector.listIncludes(basedir, includes, excludes);
            if (files.isEmpty()) {
                throw new MojoExecutionException("No matching feature file found! Please check your include and exclude values");
            }

            getLog().info("Creating test drivers for " + files.size() + " feature files:");
            File folder = createTargetFolderIfNotExists();
            for (String featureFile : files) {
                createTestDriverSourceFile(template, ctr, glueCsv, folder, featureFile, packageLine);
                ctr++;
            }

            project.addTestCompileSourceRoot(
                Paths.get(targetFolder, "generated-test-sources", "tigerbdd").toAbsolutePath().toString());
        } catch (IOException e) {
            throw new MojoExecutionException("File read/write failure!", e);
        }
    }

    private void checkParams() throws MojoExecutionException {
        if (glues.isEmpty()) {
            throw new MojoExecutionException("No glues specified! Is this what you wanted?");
        }
        if (includes.isEmpty()) {
            throw new MojoExecutionException("Includes are mandatory!");
        }
        if (!driverClassName.contains("${ctr}")) {
            throw new MojoExecutionException(
                "Driver class name does not contain ${ctr}! "
                    + "So only one driver java file will be generated and always overwritten! "
                    + "Make sure to include the '${ctr}' token to create driver files for each feature file.");
        }
    }

    private String getTemplate() throws IOException, MojoExecutionException {
        String template;
        if (templateFile == null) {
            try (InputStream is = getClass().getResourceAsStream("/driverClassTemplate.jtmpl")) {
                if (is == null) {
                    throw new MojoExecutionException("INTERNAL ERROR - Unable to find template resource file");
                }
                template = IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        } else {
            getLog().info("Using template file '" + templateFile + "'");
            template = IOUtils.toString(new File(templateFile.replace("/", File.separator)).toURI(), StandardCharsets.UTF_8);
        }
        return template;
    }

    private File createTargetFolderIfNotExists() throws MojoExecutionException {
        File folder = Paths.get(targetFolder, "generated-test-sources", "tigerbdd",
            driverPackage.replace(".", File.separator)).toFile();
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                throw new MojoExecutionException("Unable to create target folder '" + folder.getAbsolutePath() + "'");
            }
        }
        return folder;
    }

    private void createTestDriverSourceFile(String template, int ctr, String glueCsv, File folder, String featureFile,
        String packageLine)
        throws IOException {
        String featurePath = getBaseDirPrefixedFilename(featureFile);
        String currentDriverClassName = driverClassName.replace("${ctr}", String.valueOf(ctr));

        File out = Paths.get(folder.getAbsolutePath(), currentDriverClassName + ".java").toFile();

        getLog().info("    '" + featurePath + "'");
        getLog().debug("=> '" + out.getAbsolutePath() + "'");
        String driverSourceCode = template.replace("${ctr}", String.valueOf(ctr))
            .replace("${package}", packageLine)
            .replace("${driverClassName}", currentDriverClassName)
            .replace("${feature}", featurePath.replace("\\", "/"))
            .replace("${glues}", glueCsv);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            IOUtils.write(driverSourceCode, fos, StandardCharsets.UTF_8);
        }
    }

    private String getBaseDirPrefixedFilename(String file) {
        if (StringUtils.isBlank(basedir)) {
            return file;
        }
        return basedir + "/" + file;
    }

}
