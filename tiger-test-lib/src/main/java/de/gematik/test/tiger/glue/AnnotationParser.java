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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AnnotationParser {

  private final String sourceFolder;
  private final String targetFolder;

  public AnnotationParser(String sourceFolder, String targetFolder) {
    this.sourceFolder = sourceFolder;
    this.targetFolder = targetFolder;
  }

  public static void main(String[] args) throws IOException {
    AnnotationParser annotationParser =
        new AnnotationParser(
            "tiger-test-lib/src/main/java/de/gematik/test/tiger/glue/", "./doc/user_manual/");
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
    return doc.replaceAll("@(\\w+\\b)", "*$1*")
        .replace("\n\\*", "\n\n*")
        .replace("<p>", "")
        .replaceAll("(<pre>|</pre>)", "\n----\n")
        .replaceAll("(<b>|</b>)", "*")
        .replace("<br>", "")
        .replace("TGR", "##### TGR");
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
    List<String> annotationsToFind = List.of("When", "Wenn", "Dann", "Gegebensei", "Given");
    return annotations.stream()
        .filter(a -> annotationsToFind.contains(a.getName().getIdentifier()))
        .toList();
  }
}
