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
 *
 */
package de.gematik.test.tiger.lib.glue.testfixture;

import de.gematik.test.tiger.common.glue.TigerGluePackage;

/**
 * Test-only marker — present so {@link de.gematik.test.tiger.lib.glue.TigerGluePackageScanner} has
 * something to find when the canopy-extension isn't on the test classpath. Lives in its own package
 * so the assertion can be "exactly this package is discovered" rather than coupling to the package
 * of the test class.
 */
@TigerGluePackage
@SuppressWarnings("java:S2187")
public class TigerGlueMarkerForTest {}
