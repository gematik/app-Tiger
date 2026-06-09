/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.topology

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class YamlMergerTest {

    @Test
    fun `placeholder-based additional file resolves using value from earlier additional file`() {
        val tigerYaml = """
            additionalConfigurationFiles:
              - filename: defaults.yaml
              - filename: tiger-${"\${environment}"}.yaml
                baseKey: tiger
        """.trimIndent()

        val defaultsYaml = "environment: local"

        val tigerLocalYaml = """
            servers:
              demo:
                type: externalJar
        """.trimIndent()

        val config = mergeGlobalConfig(listOf(
            UploadedYamlFile("tiger.yaml", tigerYaml),
            UploadedYamlFile("defaults.yaml", defaultsYaml),
            UploadedYamlFile("tiger-local.yaml", tigerLocalYaml),
            UploadedYamlFile("tiger-external.yaml", "servers:\n  demo:\n    type: externalUrl"),
        ))

        assertThat(config.tigerConfig.servers).containsKey("demo")
        assertThat(config.tigerConfig.servers["demo"]?.type).isEqualTo("externalJar")
    }

    @Test
    fun `non-included tiger configs get a warning`() {
        val tigerYaml = """
            additionalConfigurationFiles:
              - filename: defaults.yaml
              - filename: tiger-${"\${environment}"}.yaml
                baseKey: tiger
        """.trimIndent()

        val defaultsYaml = "environment: local"

        val tigerLocalYaml = "servers:\n  demo:\n    type: externalJar"
        val tigerExternalYaml = "servers:\n  demo:\n    type: externalUrl"

        val config = mergeGlobalConfig(listOf(
            UploadedYamlFile("tiger.yaml", tigerYaml),
            UploadedYamlFile("defaults.yaml", defaultsYaml),
            UploadedYamlFile("tiger-local.yaml", tigerLocalYaml),
            UploadedYamlFile("tiger-external.yaml", tigerExternalYaml),
        ))

        assertThat(config.warnings).anyMatch { it.contains("tiger-external.yaml") && it.contains("not included") }
        assertThat(config.warnings).noneMatch { it.contains("tiger-local.yaml") && it.contains("not included") }
    }

    @Test
    fun `multiple candidate files for placeholder reference - primary root selected by additionalConfigurationFiles`() {
        val tigerYaml = """
            additionalConfigurationFiles:
              - filename: tiger-${"\${environment}"}.yaml
                baseKey: tiger
        """.trimIndent()

        val tigerLocalYaml = "servers:\n  demo:\n    type: externalJar"
        val tigerExternalYaml = "servers:\n  other:\n    type: externalUrl"

        // tiger.yaml is the only one with additionalConfigurationFiles → it's the primary root
        // placeholder can't be resolved (no defaults), so both candidates remain unincluded with warnings
        val config = mergeGlobalConfig(listOf(
            UploadedYamlFile("tiger.yaml", tigerYaml),
            UploadedYamlFile("tiger-local.yaml", tigerLocalYaml),
            UploadedYamlFile("tiger-external.yaml", tigerExternalYaml),
        ))

        assertThat(config.warnings).anyMatch { it.contains("not included") }
    }

    @Test
    fun `single root file without placeholder references still works`() {
        val yaml = """
            servers:
              myServer:
                type: zion
        """.trimIndent()

        val config = mergeGlobalConfig(listOf(UploadedYamlFile("tiger.yaml", yaml)))

        assertThat(config.tigerConfig.servers).containsKey("myServer")
        assertThat(config.warnings).isEmpty()
    }
}


