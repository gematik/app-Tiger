package de.gematik.test.tiger.glue;

import static org.junit.jupiter.api.Assertions.*;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnnotationParserTest {
  MethodVisitor methodVisitor = new MethodVisitor();
  String sourceFolder = "src/main/java/de/gematik/test/tiger/glue/";
  String targetFolder = "src/test/resources/";
  String[] files = {"HttpGlueCode.java"};

  @ParameterizedTest
  @CsvSource({
    "<p>, ''",
    "@param, *param*",
    "<b>, *",
    "<br>, ''",
    "TGR send empty, ##### TGR send empty"
  })
  void testFormatLineAt(String input, String expected) {
    assertEquals(expected, AnnotationParser.formatLine(input));
  }

  @Test
  void testMain() throws IOException {
    var annotationParser = new AnnotationParser(sourceFolder, targetFolder);

    annotationParser.extractJavaDocsToAdoc(files);

    assertTrue(Files.exists(Paths.get(targetFolder, "HttpGlueCodeCommentsOnly.adoc")));
  }

  @Test
  void testFilterAnnotations() {
    AnnotationExpr annotation1 = new SingleMemberAnnotationExpr();
    annotation1.setName("When");

    AnnotationExpr annotation2 = new SingleMemberAnnotationExpr();
    annotation2.setName("Wenn");

    AnnotationExpr annotation3 = new SingleMemberAnnotationExpr();
    annotation3.setName("Dann");

    AnnotationExpr annotation4 = new SingleMemberAnnotationExpr();
    annotation4.setName("Given");
    List<AnnotationExpr> annotations = List.of(annotation1, annotation2, annotation3, annotation4);

    List<AnnotationExpr> filteredAnnotations = methodVisitor.filterAnnotations(annotations);

    assertEquals(4, filteredAnnotations.size());
    assertEquals("When", filteredAnnotations.get(0).getNameAsString());
    assertEquals("Wenn", filteredAnnotations.get(1).getNameAsString());
    assertEquals("Dann", filteredAnnotations.get(2).getNameAsString());
    assertEquals("Given", filteredAnnotations.get(3).getNameAsString());
  }
}
