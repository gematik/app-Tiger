package io.cucumber.core.plugin.report;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import io.cucumber.core.plugin.report.Evidence.Type;
import io.cucumber.core.plugin.report.EvidenceReport.ReportContext;
import io.cucumber.core.plugin.report.EvidenceReport.Step;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceRendererTest {

  private EvidenceRenderer underTest;

  private final HtmlEvidenceRenderer htmlEvidenceRenderer = spy(new HtmlEvidenceRenderer());

  @BeforeEach
  void setUp() {
    underTest = new EvidenceRenderer(htmlEvidenceRenderer);
  }

  @Getter
  public static class Teststructure {

    public String ort = "Küche";

    public String protagonist = "Mops";

    public Inner handlung = new Inner();

    @Getter
    public static class Inner {

      public String bezeichnung = "gehen";

      public String beschreibung = "Gleichmäßige, aufeinander abgestimmte Bewegungen zum Zwecke der Fortbewegung";
    }

  }

  @Test
  @DisplayName("All the necessary information should be found withing the rendered template")
  @SneakyThrows
  void render_AllTheNecessaryInformationShouldBeFoundWithingTheRenderedTemplate() {
    // Arrange
    final EvidenceReport report =
        new EvidenceReport(
            new ReportContext(
                "fancy scenario",
                URI.create("bluba://bla")),
            List.of(
                new Step("42"),
                new Step("Mopslied",
                    List.of(
                        new Evidence(Type.INFO, "Ein Mops kam in die Küche", new Teststructure()),
                        new Evidence(Type.WARN, "und stahl dem Koch ein...", List.of("Ei")),
                        new Evidence(Type.ERROR, "Da nahm der Koch die Kelle"),
                        new Evidence(Type.FATAL, "und Schlug den Mops zu Brei", "oh nein"),
                        new Evidence(Type.FATAL, "dann kamen viele Möpse",
                            new int[]{1, 2, 3, 4, 42}),
                        new Evidence(Type.FATAL, "und gruben ihm ein Grab",
                            new Double[]{17.2}),
                        new Evidence(Type.FATAL, "sie stlelten drauf nen Grabstein",
                            new String[]{"Puggy mac Pugface", "nun hat er keinen hunger mehr"}),
                        new Evidence(Type.FATAL, "worauf geschrieben stand",
                            1337)))
            ));

    // Act
    var result = underTest.render(report);
    saveAndPrintForTemplateDebuggung(
        "render_AllTheNecessaryInformationShouldBeFoundWithingTheRenderedTemplate.html", result);

    // Assert
    SoftAssertions.assertSoftly(soft -> {
      soft.assertThat(result)
          .contains(
              report.getContext().getScenario(),
              "Ein Mops kam in die Küche",
              "Da nahm der Koch die Kelle",
              "Ei",
              "oh nein",
              "Puggy mac Pugface",
              "42",
              "17.2",
              "1337"
          );

      soft.assertThat(result)
          .containsIgnoringCase("<html")
          .containsIgnoringCase("</html>");
    });
  }

  @Test
  @DisplayName("on Html Rendering errors the data should be returned as json String")
  @SneakyThrows
  void render_OnHtmlRenderingErrorsTheDataShouldBeReturnedAsJsonString() {
    // Arrange
    final EvidenceReport report =
        new EvidenceReport(
            new ReportContext(
                "fancy scenario",
                URI.create("bluba://bla")),
            List.of(
                new Step("42"),
                new Step("Mopslied",
                    List.of(
                        new Evidence(Type.INFO, "Ein Mops kam in die Küche", new Teststructure()),
                        new Evidence(Type.WARN, "und stahl dem Koch ein...", List.of("Ei")),
                        new Evidence(Type.ERROR, "Da nahm der Koch die Kelle"),
                        new Evidence(Type.FATAL, "und Schlug den Mops zu Brei", "oh nein"),
                        new Evidence(Type.FATAL, "dann kamen viele Möpse",
                            new int[]{1, 2, 3, 4, 42}),
                        new Evidence(Type.FATAL, "und gruben ihm ein Grab",
                            new Double[]{17.2}),
                        new Evidence(Type.FATAL, "sie stlelten drauf nen Grabstein",
                            new String[]{"Puggy mac Pugface", "nun hat er keinen hunger mehr"}),
                        new Evidence(Type.FATAL, "worauf geschrieben stand",
                            1337)))
            ));
    doThrow(IOException.class)
        .when(htmlEvidenceRenderer)
        .render(report);

    // Act
    var result = underTest.render(report);
    saveAndPrintForTemplateDebuggung(
        "render_AllTheNecessaryInformationShouldBeFoundWithingTheRenderedTemplate.html", result);

    // Assert
    SoftAssertions.assertSoftly(soft -> {
      soft.assertThat(result)
          .contains(
              report.getContext().getScenario(),
              report.getContext().getFeature().toString(),
              "Ein Mops kam in die Küche",
              "Da nahm der Koch die Kelle",
              "Ei",
              "oh nein",
              "Puggy mac Pugface",
              "42",
              "17.2",
              "1337"
          );
      soft.assertThat(result)
          .startsWith("{")
          .endsWith("}");
    });

  }

  private void saveAndPrintForTemplateDebuggung(String filename, String result)
      throws IOException {
    final Path testOutputDir = Paths.get("target", "testoutput", getClass().getSimpleName());
    if (Files.notExists(testOutputDir)) {
      Files.createDirectories(testOutputDir);
    }
    System.out.println(
        "Report Path: " + Files.write(testOutputDir.resolve(filename),
                result.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)
            .toUri());
  }
}
