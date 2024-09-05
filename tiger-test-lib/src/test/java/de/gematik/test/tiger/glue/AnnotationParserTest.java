/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
    "@param, '[.indent]\n*param*'",
    "<b>, '[.indent]\n*'",
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
