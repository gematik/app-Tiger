/*
 * Copyright (c) 2024 gematik GmbH
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

package io.cucumber.core.runner;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;

/** A wrapper for io.cucumber.plugin.event.TestCase to make it public. */
@RequiredArgsConstructor(staticName = "of")
@Slf4j
public class TestCaseDelegate {

  @Delegate private final io.cucumber.plugin.event.TestCase delegate;

  @SneakyThrows
  public boolean isDryRun() {
    if (delegate instanceof TestCase) {
      ExecutionMode executionMode =
          (ExecutionMode) FieldUtils.readField(delegate, "executionMode", true);
      return ExecutionMode.DRY_RUN.equals(executionMode);
    } else {
      return false;
    }
  }
}
