/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.adapter.mojos;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class GenerateDriverMojoTest {

    @Test
    void testEmptyIncludes_NOK() {
        final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
        mavenGoal.setIncludes(new ArrayList<>());
        assertThatThrownBy(mavenGoal::execute).isInstanceOf(MojoExecutionException.class);
    }

    @Test
    void testEmptyGlues_NOK() {
        final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
        mavenGoal.setGlues(new ArrayList<>());
        assertThatThrownBy(mavenGoal::execute).isInstanceOf(MojoExecutionException.class);
    }

    @Test
    void testDriverClassNameNoCtr_NOK() {
        final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
        mavenGoal.setDriverClassName("TestDriverClassName");
        assertThatThrownBy(mavenGoal::execute).isInstanceOf(MojoExecutionException.class);
    }

    @Test
    void testSkip() throws IOException, MojoExecutionException {
        final File folder = Paths.get("target", "generated-test-sources/tigerbdd").toFile();
        FileUtils.deleteDirectory(folder);
        final GenerateDriverMojo mavenGoal = new GenerateDriverMojo();
        mavenGoal.setSkip(true);
        mavenGoal.execute();
        assertThat(folder).doesNotExist();
    }
}
