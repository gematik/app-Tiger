/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.adapter.mojos;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import org.apache.maven.plugin.logging.Log;

@Builder
@Data
public class GenerateDriverProperties {

    public static final String DEFAULT_TAGS = "not @Ignore";

    private final List<String> glues;
    private final String driverClassName;
    private final Path templateFile;
    private final String driverPackage;
    private Path outputFolder;
    private String featuresRootFolder;
    private final String cucumberFilterTags = System.getProperty("cucumber.filter.tags", DEFAULT_TAGS);

    public void log(Log log) {
        log.info("Using features root folder: " + featuresRootFolder);
        log.info("Using output folder: " + outputFolder);
        log.debug("Using glues:" + String.join(", ", glues));
        log.debug("Using tags:" + getCucumberFilterTags());
    }

    public Path getOutputFolderToPackage() {
        return getOutputFolder().resolve(Optional.ofNullable(getDriverPackage()).orElse("").replace(".", File.separator));
    }
}
