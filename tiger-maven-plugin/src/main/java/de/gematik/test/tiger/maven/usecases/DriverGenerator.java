/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.usecases;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public class DriverGenerator {

    public static final String COUNTER_REPLACEMENT_TOKEN = "${ctr}";
    private final String commaseparatedGlues;
    private final String driverClassName;
    private final Path templateFile;
    private final Logger log;
    private final String driverPackage;
    private final Path outputFolder;

    public DriverGenerator(final List<String> glues, final String driverPackage,
        final Path outputFolder,
        final String driverClassName, final Path templateFile, final Logger logger) {
        this.driverPackage = driverPackage;
        this.outputFolder = StringUtils.isBlank(driverPackage) ?
            outputFolder :
            outputFolder.resolve(driverPackage.replace(".", File.separator));
        commaseparatedGlues = toCommaseparatedQuotedList(glues);
        this.driverClassName = driverClassName;
        this.templateFile = templateFile;
        log = logger;
    }

    private String toCommaseparatedQuotedList(final List<String> glues) {
        return Stream.concat(Stream.of(
            "de.gematik.test.tiger.glue"
        ), glues.stream()).distinct().map(this::withQuotes).collect(Collectors.joining(", "));
    }

    private String withQuotes(final String it) {
        return "\"" + it + "\"";
    }

    public void generateDriverForFeatureFiles(final List<String> files) throws IOException {
        createTargetFolderIfNotExists();

        int ctr = 1;
        for (final String featureFile : files) {
            createTestDriverSourceFile(ctr++, featureFile);
        }
    }

    private void createTargetFolderIfNotExists() throws IOException {
        if (outputFolder.toFile().exists()) {
            FileUtils.deleteDirectory(outputFolder.toFile());
        }
        Files.createDirectories(outputFolder);
    }

    private void createTestDriverSourceFile(final int ctr,
        final String featurePath)
        throws IOException {
        final String currentDriverClassName = driverClassName.replace(COUNTER_REPLACEMENT_TOKEN,
            String.format("%03d", ctr));

        final String driverSourceCode = driverClassSourceCode(ctr, featurePath,
            currentDriverClassName);

        write(currentDriverClassName, driverSourceCode);
    }

    private String driverClassSourceCode(final int ctr, final String featurePath,
        final String currentDriverClassName) throws IOException {
        final String packageLine = StringUtils.isBlank(
            driverPackage) ? "" : "package " + driverPackage + ";\n";
        log.info("    '" + featurePath + "'");
        return getTemplate().replace(COUNTER_REPLACEMENT_TOKEN,
                String.valueOf(ctr))
            .replace("${package}", packageLine)
            .replace("${driverClassName}", currentDriverClassName)
            .replace("${feature}", featurePath.replace("\\", "/"))
            .replace("${glues}", commaseparatedGlues);
    }

    private void write(final String currentDriverClassName, final String driverSourceCode)
        throws IOException {
        final Path sourceFile = outputFolder.resolve(currentDriverClassName + ".java");
        log.info("=> '" + sourceFile.toAbsolutePath() + "'");
        Files.write(sourceFile, driverSourceCode.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String getTemplate() throws IOException {
        if (templateFile == null) {
            try (final InputStream is = getClass().getResourceAsStream(
                "/driverClassTemplate.jtmpl")) {
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        } else {
            log.info("Using template file '" + templateFile + "'");
            return Files.readString(templateFile, StandardCharsets.UTF_8);
        }
    }

}
