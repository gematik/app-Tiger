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

package de.gematik.test.tiger.maven.usecases;

import de.gematik.test.tiger.maven.adapter.mojos.GenerateDriverProperties;
import java.io.File;
import java.io.FileNotFoundException;
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
import org.apache.maven.plugin.logging.Log;

public class DriverGenerator {

    public static final String COUNTER_REPLACEMENT_TOKEN = "${ctr}";

    private GenerateDriverProperties props;

    private final Log log;

    public DriverGenerator(final GenerateDriverProperties props, Log log) {
      this.props = props;
      this.log = log;
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
        if (props.getOutputFolder().toFile().exists()) {
            FileUtils.deleteDirectory(props.getOutputFolder().toFile());
        }
        Files.createDirectories(props.getOutputFolderToPackage());
    }

    private void createTestDriverSourceFile(final int ctr, final String featurePath)
        throws IOException {
        final String currentDriverClassName = props.getDriverClassName()
            .replace(COUNTER_REPLACEMENT_TOKEN, String.format("%03d", ctr));

        final String driverSourceCode = getDriverClassSourceCodeAsString(ctr, featurePath, currentDriverClassName);
        final String filePath = writeToDriverSourceFile(currentDriverClassName, driverSourceCode);
        log.info(" Feature '" + featurePath + "'");
        log.info(" Java => '" + filePath + "'");
    }

    private String getDriverClassSourceCodeAsString(final int ctr, final String featurePath,
        final String currentDriverClassName) throws IOException {
        final String packageLine = StringUtils.isBlank(
            props.getDriverPackage()) ? "" : "package " + props.getDriverPackage() + ";\n";
        return getTemplate().replace(COUNTER_REPLACEMENT_TOKEN,
                String.valueOf(ctr))
            .replace("${package}", packageLine)
            .replace("${driverClassName}", currentDriverClassName)
            .replace("${feature}", featurePath.replace("\\", "/"))
            .replace("${glues}", toCommaseparatedQuotedList(props.getGlues()))
            .replace("${tags}", props.getCucumberFilterTags());
    }

    private String writeToDriverSourceFile(final String currentDriverClassName, final String driverSourceCode)
        throws IOException {
        final Path sourceFile = props.getOutputFolderToPackage().resolve(currentDriverClassName + ".java");
        Files.write(sourceFile, driverSourceCode.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
        return sourceFile.toFile().getAbsolutePath();
    }

    private String getTemplate() throws IOException {
        if (props.getTemplateFile() == null) {
            try (final InputStream is = getClass().getResourceAsStream("/driverClassTemplate.jtmpl")) {
                if (is == null) {
                    throw new FileNotFoundException("Unable to find template file resource '/driverClassTemplate.jtmpl' in jar!");
                }
                log.debug("Using template file from jar ressource");
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        } else {
            log.info("Using template file '" + props.getTemplateFile() + "'");
            return Files.readString(props.getTemplateFile(), StandardCharsets.UTF_8);
        }
    }

}
