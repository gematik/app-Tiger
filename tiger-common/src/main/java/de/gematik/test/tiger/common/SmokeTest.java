/*
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
package de.gematik.test.tiger.common;

/**
 * Marker interface to tag a test class or method as a smoke test.
 *
 * <p>Smoke tests must:
 *
 * <ul>
 *   <li>complete in under 30 seconds
 *   <li>require no external infrastructure (no Docker, no network, no Spring context startup)
 *   <li>cover a critical path of the framework
 * </ul>
 *
 * <p>Smoke tests are run on every master build and every release build via {@code -P=Smoke}.
 * Exhaustive coverage remains with the Nightly pipeline ({@code -P=WithLongrunner}).
 *
 * @see LongRunnerTest
 */
public interface SmokeTest {}
