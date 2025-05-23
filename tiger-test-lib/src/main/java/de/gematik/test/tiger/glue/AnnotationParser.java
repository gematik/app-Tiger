/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.glue;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AnnotationParser {

  private final String sourceFolder;
  private final String targetFolder;

  public AnnotationParser(String sourceFolder, String targetFolder) {
    this.sourceFolder = sourceFolder;
    this.targetFolder = targetFolder;
  }

  public static void main(String[] args) throws IOException {
    File f = new File(".");
    String folder = "src/main/java/de/gematik/test/tiger/glue/";
    String docFolder = "../doc/user_manual/";
    if (!f.getAbsolutePath().endsWith("tiger-test-lib/.")) {
      folder = "tiger-test-lib/" + folder;
      docFolder = "./doc/user_manual/";
    }
    AnnotationParser annotationParser = new AnnotationParser(folder, docFolder);
    annotationParser.extractJavaDocsToAdoc(args);
  }

  public void extractJavaDocsToAdoc(String[] args) throws IOException {
    for (String arg : args) {
      Path path = Paths.get(sourceFolder, arg);
      String lines = Files.readString(path, StandardCharsets.UTF_8);
      CompilationUnit compilationUnit = StaticJavaParser.parse(lines);
      List<String> list = new ArrayList<>();
      compilationUnit.accept(new MethodVisitor(), list);
      String filename = arg.replace(".java", "CommentsOnly.adoc");
      String fileContent =
          formatLine(list.stream().collect(Collectors.joining(System.lineSeparator())));
      Files.writeString(Path.of(targetFolder, filename), fileContent, StandardCharsets.UTF_8);
    }
  }

  public static String formatLine(String doc) {
    doc =
        doc.replaceAll("@(\\w+\\b)", "*$1*")
            .replace("\n\\*", "\n\n*")
            .replace("<p>", "")
            .replaceAll("(<pre>|</pre>)", "----")
            .replaceAll("(<b>|</b>)", "*")
            .replace("<br>", "");
    AtomicBoolean isDescription = new AtomicBoolean(false);
    doc =
        Arrays.stream(doc.split("\n"))
            .map(
                line -> {
                  if (line.startsWith("TGR")) {
                    isDescription.set(false);
                    return "##### " + line;
                  } else if (line.trim().isEmpty()) {
                    isDescription.set(false);
                    return "";
                  } else {
                    if (!isDescription.get()) {
                      isDescription.set(true);
                      return "[.indent]\n" + line;
                    } else {
                      return line;
                    }
                  }
                })
            .collect(Collectors.joining("\n"));

    return doc;
  }
}

class MethodVisitor extends VoidVisitorAdapter<List<String>> {
  @Override
  public void visit(MethodDeclaration n, List<String> str) {
    NodeList<AnnotationExpr> annotations = n.getAnnotations();
    List<AnnotationExpr> filteredAnnotations = filterAnnotations(annotations);
    if (filteredAnnotations.isEmpty()) {
      return;
    } else {
      Optional<JavadocComment> javadocComment = n.getJavadocComment();
      filteredAnnotations.stream()
          .map(SingleMemberAnnotationExpr.class::cast)
          .map(SingleMemberAnnotationExpr::getMemberValue)
          .map(Node::toString)
          .map(m -> m.substring(1, m.length() - 1))
          .forEach(str::add);
      javadocComment.ifPresent(comment -> str.add(comment.parse().toText()));
    }
    super.visit(n, str);
  }

  public List<AnnotationExpr> filterAnnotations(List<AnnotationExpr> annotations) {
    List<String> annotationsToFind =
        List.of("When", "Wenn", "Dann", "Then", "And", "Und", "But", "Aber", "Gegebensei", "Given");
    return annotations.stream()
        .filter(a -> annotationsToFind.contains(a.getName().getIdentifier()))
        .toList();
  }
}
