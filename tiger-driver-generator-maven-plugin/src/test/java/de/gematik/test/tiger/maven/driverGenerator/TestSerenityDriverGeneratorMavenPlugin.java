/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.driverGenerator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

public class TestSerenityDriverGeneratorMavenPlugin {
    @Test
    public void testEmptyIncludes_NOK() {
        SerenityDriverGeneratorMavenPlugin p = new SerenityDriverGeneratorMavenPlugin();
        p.setIncludes(new ArrayList<>());
        assertThatThrownBy(p::execute).isInstanceOf(MojoExecutionException.class);
    }
    @Test
    public void testEmptyGlues_NOK() {
        SerenityDriverGeneratorMavenPlugin p = new SerenityDriverGeneratorMavenPlugin();
        p.setGlues(new ArrayList<>());
        assertThatThrownBy(p::execute).isInstanceOf(MojoExecutionException.class);
    }
    @Test
    public void testDriverClassNameNoCtr_NOK() {
        SerenityDriverGeneratorMavenPlugin p = new SerenityDriverGeneratorMavenPlugin();
        p.setDriverClassName("TestDriverClassName");
        assertThatThrownBy(p::execute).isInstanceOf(MojoExecutionException.class);
    }
    @Test
    public void testSkip() throws IOException, MojoExecutionException {
        File folder = Paths.get("target", "generated-test-sources/tigerbdd").toFile();
        FileUtils.deleteDirectory(folder);
        SerenityDriverGeneratorMavenPlugin p = new SerenityDriverGeneratorMavenPlugin();
        p.setSkip(true);
        p.execute();
        assertThat(folder).doesNotExist();
    }
}
