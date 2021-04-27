/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import de.gematik.test.tiger.lib.parser.model.Testcase;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class JavaTestParser implements ITestParser {

    private final Map<String, List<Testcase>> parsedTestcasesPerAfo = new HashMap<>();
    private final Map<String, Testcase> parsedTestcases = new HashMap<>();
    private final Map<String, Testcase> unreferencedTestcases = new HashMap<>();

    @Override
    public void parseDirectory(final File rootDir) {
        if (rootDir == null) {
            log.warn("Invalid test source NULL root dir");
        } else {
            final File[] files = rootDir.listFiles();
            if (files == null) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Invalid test source root dir %s", rootDir.getAbsolutePath()));
                }
            } else {
                Arrays.asList(files).forEach(f -> {
                    if (f.isDirectory()) {
                        parseDirectory(f);
                    } else if (f.getName().endsWith(".java")) {
                        inspectFile(f);
                    }
                });
            }
        }
    }

    private void inspectFile(final File f) {
        try (final FileInputStream in = new FileInputStream(f)) {
            final CompilationUnit cu = StaticJavaParser.parse(in);
            new MethodVisitor(this).visit(cu, null);

        } catch (final IOException ioex) {
            throw new TestParserException("Unable to parse " + f.getAbsolutePath(), ioex);
        }
    }

    @Override
    public Map<String, Testcase> getTestcasesWithoutAfo() {
        return unreferencedTestcases;
    }

    /**
     * Simple visitor implementation for visiting MethodDeclaration nodes, checking for Afo and Test annotations.
     */
    private static class MethodVisitor extends VoidVisitorAdapter<Object> {

        private final JavaTestParser parser;

        MethodVisitor(final JavaTestParser parser) {
            this.parser = parser;
        }

        private static String getFullyQualifiedName(final ClassOrInterfaceDeclaration testClass) {
            return testClass.getParentNode()
                .flatMap(MethodVisitor::getClass)
                .map(parentClass -> getFullyQualifiedName(parentClass) + "." + testClass.getNameAsString())

                .orElseGet(() -> getCompilationUnit(testClass)
                    .flatMap(CompilationUnit::getPackageDeclaration)
                    .map(PackageDeclaration::getNameAsString)
                    .map(packageName -> packageName + "." + testClass.getNameAsString())

                    .orElse(testClass.getNameAsString()));
        }

        private static Optional<ClassOrInterfaceDeclaration> getClass(final Node method) {
            Node clazz = method;
            while (!(clazz instanceof ClassOrInterfaceDeclaration)) {
                final Optional<Node> cpn = clazz.getParentNode();
                if (cpn.isPresent()) {
                    clazz = cpn.get();
                } else {
                    return Optional.empty();
                }
            }
            return Optional.of((ClassOrInterfaceDeclaration) clazz);
        }

        private static Optional<CompilationUnit> getCompilationUnit(final Node clazz) {
            Node pkgs = clazz;
            while (!(pkgs instanceof CompilationUnit)) {
                final Optional<Node> pn = pkgs.getParentNode();
                if (pn.isPresent()) {
                    pkgs = pn.get();
                } else {
                    return Optional.empty();
                }
            }
            return Optional.of((CompilationUnit) pkgs);
        }

        @Override
        public void visit(final MethodDeclaration n, final Object args) {
            visitMethodAndAddAfoToTestcaseListIfPresent(n);
        }

        private void visitMethodAndAddAfoToTestcaseListIfPresent(final MethodDeclaration n) {
            final String methodname = n.getNameAsString();
            final boolean test = n.getAnnotations().stream()
                .filter(ano -> "Test".equals(ano.getNameAsString()))
                .map(ano -> true)
                .findAny()
                .orElse(false);
            if (test) {
                final String clazzname = getFullyQualifiedName(
                    (ClassOrInterfaceDeclaration) n.getParentNode().orElseThrow(
                        () -> new TestParserException((
                            String.format("Internal Error. Test Method has no parent node. Method name is %s!",
                                methodname)))));
                final Testcase tc = new Testcase();
                tc.setClazz(clazzname);
                tc.setMethod(methodname);
                parser.parsedTestcases.putIfAbsent(tc.getClazz() + ":" + tc.getMethod(), tc);
                final AtomicReference<Boolean> ref = new AtomicReference<>(false);
                n.getAnnotations().stream()
                    .filter(afo -> "Afo".equals(afo.getNameAsString()))
                    .forEach(afo -> {
                        ref.set(true);
                        addTestCaseToAfo(tc, afo);
                    });
                if (!ref.get()) {
                    parser.unreferencedTestcases.putIfAbsent(tc.getClazz() + ":" + tc.getMethod(), tc);
                }
            }
        }

        private void addTestCaseToAfo(final Testcase tc, final AnnotationExpr afo) {
            if (afo instanceof SingleMemberAnnotationExpr) {
                final String id = ((SingleMemberAnnotationExpr) afo).getMemberValue().asStringLiteralExpr().asString();
                parser.parsedTestcasesPerAfo.computeIfAbsent(id, k -> new ArrayList<>());
                parser.parsedTestcasesPerAfo.get(id).add(tc);
            } else {
                throw new TestParserException(
                    "Unsupported Afo Annotation detected in " + tc.getClazz() + ":" + tc.getMethod() + "!");
            }
        }
    }
}
