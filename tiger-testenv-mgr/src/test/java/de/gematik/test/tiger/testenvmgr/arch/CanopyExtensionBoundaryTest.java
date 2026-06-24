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
package de.gematik.test.tiger.testenvmgr.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit boundary test enforcing the post-extraction invariant: <em>no class in {@code
 * de.gematik.test.tiger.{common,proxy,rbel,testenvmgr}} may depend on any canopy-named class</em>.
 * Since the canopy adapter has been extracted to its own module (<a
 * href="../../../../../../../tiger-canopy-extension/">{@code tiger-canopy-extension}</a>, package
 * {@code de.gematik.test.tiger.canopy.extension..}) there are no longer any in-tree carve-outs at
 * all — the rule is now black-and-white.
 *
 * <p>This test stays in {@code tiger-testenv-mgr} so any accidental reintroduction of a typed
 * canopy reference into the core packages fails fast at unit-test time, regardless of whether the
 * canopy module is on the test classpath.
 *
 * <p>The {@code DockerServer} discovers the canopy server type by its {@link
 * de.gematik.test.tiger.testenvmgr.servers.TigerServerType TigerServerType} string token ({@code
 * "canopy"}); that is a runtime / configuration coupling, not a compile-time one, and does not
 * violate the rule.
 */
class CanopyExtensionBoundaryTest {

  @Test
  void coreModulesMustNotDependOnCanopyTypes() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(
                "de.gematik.test.tiger.common",
                "de.gematik.test.tiger.proxy",
                "de.gematik.test.tiger.rbel",
                "de.gematik.test.tiger.testenvmgr");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage(
                "de.gematik.test.tiger.common..",
                "de.gematik.test.tiger.proxy..",
                "de.gematik.test.tiger.rbel..",
                "de.gematik.test.tiger.testenvmgr..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "de.gematik.test.tiger.canopy..",
                "de.gematik.test.tiger.canopy.client..",
                "de.gematik.test.tiger.canopy.extension..");

    rule.check(classes);
  }
}
