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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;

/**
 * Drives {@link RetryingTest}: provides one invocation context per attempt, stopping the stream
 * once an attempt passes so that IntelliJ shows each attempt as a separate tree node and no
 * unnecessary attempts are started after a pass.
 */
@Slf4j
public class RetryingTestExtension implements TestTemplateInvocationContextProvider {

  @Override
  public boolean supportsTestTemplate(ExtensionContext context) {
    return context
        .getTestMethod()
        .map(m -> m.isAnnotationPresent(RetryingTest.class))
        .orElse(false);
  }

  @Override
  public @NonNull Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      ExtensionContext context) {
    int maxAttempts =
        context
            .getTestMethod()
            .map(m -> m.getAnnotation(RetryingTest.class).maxAttempts())
            .orElse(3);

    AtomicBoolean passed = new AtomicBoolean(false);

    return Stream.iterate(
            1, attempt -> !passed.get() && attempt <= maxAttempts, attempt -> attempt + 1)
        .map(attempt -> new RetryInvocationContext(attempt, maxAttempts, passed));
  }

  @RequiredArgsConstructor
  private static class RetryInvocationContext implements TestTemplateInvocationContext {

    private final int attempt;
    private final int maxAttempts;
    private final AtomicBoolean passed;

    @Override
    public @NonNull String getDisplayName(int invocationIndex) {
      return "Attempt " + attempt + " of " + maxAttempts;
    }

    @Override
    public @NonNull List<Extension> getAdditionalExtensions() {
      return List.of(
          (AfterEachCallback)
              ctx -> {
                if (ctx.getExecutionException().isEmpty()) {
                  passed.set(true);
                  log.debug(
                      "Test {} passed on attempt {}/{}",
                      ctx.getDisplayName(),
                      attempt,
                      maxAttempts);
                } else {
                  if (attempt < maxAttempts) {
                    log.warn(
                        "Test {} failed on attempt {}/{}, retrying... ({})",
                        ctx.getDisplayName(),
                        attempt,
                        maxAttempts,
                        ctx.getExecutionException().get().getMessage());
                  } else {
                    log.error(
                        "Test {} failed on all {} attempts", ctx.getDisplayName(), maxAttempts);
                  }
                }
              });
    }
  }
}
