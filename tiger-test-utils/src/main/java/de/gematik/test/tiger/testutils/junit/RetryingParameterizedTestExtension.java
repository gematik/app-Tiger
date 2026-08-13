/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.testutils.junit;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.jupiter.params.support.ParameterDeclaration;
import org.junit.jupiter.params.support.ParameterDeclarations;

/**
 * Drives {@link RetryingParameterizedTest}.
 *
 * <p>Scans the test method for any annotation that is itself meta-annotated with {@link
 * ArgumentsSource} (i.e. {@code @MethodSource}, {@code @ValueSource}, {@code @CsvSource}, etc.),
 * instantiates the corresponding {@link ArgumentsProvider}, and produces a lazy retry-stream for
 * each argument set.
 *
 * <p>The outer stream is: {@code argSet₁_attempt₁, [argSet₁_attempt₂, ...], argSet₂_attempt₁, ...}
 * where each per-argset sub-stream stops as soon as one attempt passes.
 */
@Slf4j
public class RetryingParameterizedTestExtension implements TestTemplateInvocationContextProvider {

  @Override
  public boolean supportsTestTemplate(ExtensionContext context) {
    return context
        .getTestMethod()
        .map(m -> m.isAnnotationPresent(RetryingParameterizedTest.class))
        .orElse(false);
  }

  @Override
  public @NonNull Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      ExtensionContext context) {
    int maxAttempts =
        context
            .getTestMethod()
            .map(m -> m.getAnnotation(RetryingParameterizedTest.class).maxAttempts())
            .orElse(3);

    List<Object[]> allArgSets = collectArguments(context);

    if (allArgSets.isEmpty()) {
      throw new IllegalStateException(
          "No @ArgumentsSource-backed annotations found on "
              + context.getRequiredTestMethod().getName()
              + ". Add @MethodSource, @ValueSource, @CsvSource, etc.");
    }

    return allArgSets.stream()
        .flatMap(
            args -> {
              AtomicBoolean passed = new AtomicBoolean(false);
              return Stream.iterate(
                      1, attempt -> !passed.get() && attempt <= maxAttempts, attempt -> attempt + 1)
                  .map(
                      attempt -> new RetryArgInvocationContext(attempt, maxAttempts, args, passed));
            });
  }

  /**
   * Scans all annotations on the test method for those carrying an {@link ArgumentsSource}
   * meta-annotation, instantiates each provider, and collects all argument sets.
   */
  @SneakyThrows
  @SuppressWarnings({"unchecked", "rawtypes", "java:S3011"})
  private static List<Object[]> collectArguments(ExtensionContext context) {
    Method testMethod = context.getRequiredTestMethod();
    List<Object[]> result = new ArrayList<>();
    ParameterDeclarations paramDeclarations = buildParameterDeclarations(testMethod);

    for (Annotation annotation : testMethod.getAnnotations()) {
      ArgumentsSource argSource = annotation.annotationType().getAnnotation(ArgumentsSource.class);
      if (argSource == null) {
        continue;
      }

      var constructor = argSource.value().getDeclaredConstructor();
      constructor.setAccessible(true);
      ArgumentsProvider provider = constructor.newInstance();

      if (provider instanceof AnnotationConsumer consumer) {
        consumer.accept(annotation);
      }

      try (Stream<? extends Arguments> argStream =
          provider.provideArguments(paramDeclarations, context)) {
        argStream.map(Arguments::get).forEach(result::add);
      }
    }

    return result;
  }

  /** Builds a {@link ParameterDeclarations} from the test method's parameter list. */
  private static @NonNull ParameterDeclarations buildParameterDeclarations(
      @NonNull Method testMethod) {
    Parameter[] params = testMethod.getParameters();
    List<ParameterDeclaration> declarations = new ArrayList<>();
    for (int i = 0; i < params.length; i++) {
      final int idx = i;
      final Parameter param = params[i];
      declarations.add(
          new ParameterDeclaration() {
            @Override
            public @NonNull AnnotatedElement getAnnotatedElement() {
              return param;
            }

            @Override
            public @NonNull Class<?> getParameterType() {
              return param.getType();
            }

            @Override
            public int getParameterIndex() {
              return idx;
            }

            @Override
            public @NonNull Optional<String> getParameterName() {
              return param.isNamePresent() ? Optional.of(param.getName()) : Optional.empty();
            }
          });
    }

    return new ParameterDeclarations() {
      @Override
      public @NonNull List<ParameterDeclaration> getAll() {
        return declarations;
      }

      @Override
      public @NonNull Optional<ParameterDeclaration> getFirst() {
        return declarations.isEmpty() ? Optional.empty() : Optional.of(declarations.get(0));
      }

      @Override
      public @NonNull Optional<ParameterDeclaration> get(int parameterIndex) {
        return parameterIndex < declarations.size()
            ? Optional.of(declarations.get(parameterIndex))
            : Optional.empty();
      }

      @Override
      public @NonNull AnnotatedElement getSourceElement() {
        return testMethod;
      }

      @Override
      public @NonNull String getSourceElementDescription() {
        return testMethod.getName();
      }
    };
  }

  @RequiredArgsConstructor
  private static class RetryArgInvocationContext implements TestTemplateInvocationContext {

    private final int attempt;
    private final int maxAttempts;
    private final Object[] args;
    private final AtomicBoolean passed;

    @Override
    public @NonNull String getDisplayName(int invocationIndex) {
      return Arrays.toString(args) + " - attempt " + attempt + " of " + maxAttempts;
    }

    @Override
    public @NonNull List<Extension> getAdditionalExtensions() {
      return List.of(
          // Resolve method-source arguments by position; other ParameterResolvers (e.g.
          // TigerExtension) handle parameters beyond the argument count.
          new ParameterResolver() {
            @Override
            public boolean supportsParameter(
                @NonNull ParameterContext paramCtx, @NonNull ExtensionContext extCtx) {
              return paramCtx.getIndex() < args.length;
            }

            @Override
            public @NonNull Object resolveParameter(
                @NonNull ParameterContext paramCtx, @NonNull ExtensionContext extCtx) {
              return args[paramCtx.getIndex()];
            }
          },
          // Track pass/fail for the lazy-stream predicate
          (AfterEachCallback)
              ctx -> {
                if (ctx.getExecutionException().isEmpty()) {
                  passed.set(true);
                  log.debug(
                      "{} {} passed on attempt {}/{}",
                      ctx.getDisplayName(),
                      Arrays.toString(args),
                      attempt,
                      maxAttempts);
                } else {
                  if (attempt < maxAttempts) {
                    log.warn(
                        "{} {} failed on attempt {}/{}, retrying... ({})",
                        ctx.getDisplayName(),
                        Arrays.toString(args),
                        attempt,
                        maxAttempts,
                        ctx.getExecutionException().get().getMessage());
                  } else {
                    log.error(
                        "{} {} failed on all {} attempts",
                        ctx.getDisplayName(),
                        Arrays.toString(args),
                        maxAttempts);
                  }
                }
              });
    }
  }
}
