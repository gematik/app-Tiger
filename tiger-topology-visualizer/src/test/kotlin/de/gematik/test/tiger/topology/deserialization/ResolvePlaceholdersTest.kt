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

package de.gematik.test.tiger.topology.deserialization

import de.gematik.test.tiger.topology.UploadedYamlFile
import de.gematik.test.tiger.topology.mergeGlobalConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResolvePlaceholdersTest {

    private fun globalConfigOf(yaml: String) =
        mergeGlobalConfig(listOf(UploadedYamlFile("test.yaml", yaml)))

    @Test
    fun `should resolve a simple placeholder with tiger prefix`() {
        val config = globalConfigOf("""
            servers:
              serverA:
                type: docker
                serverPort: "8080"
        """.trimIndent())

        val result = resolvePlaceholders("\${tiger.servers.serverA.serverPort}", config)

        assertThat(result).isEqualTo("8080")
    }

    @Test
    fun `should resolve multiple placeholders in one string`() {
        val config = globalConfigOf("""
            servers:
              serverA:
                type: docker
                hostname: my-host
                serverPort: "8080"
        """.trimIndent())

        val result = resolvePlaceholders(
            "http://\${tiger.servers.serverA.hostname}:\${tiger.servers.serverA.serverPort}",
            config
        )

        assertThat(result).isEqualTo("http://my-host:8080")
    }

    @Test
    fun `should resolve transitive placeholders`() {
        val config = globalConfigOf("""
            servers:
              serverA:
                type: docker
                serverPort: "${'$'}{tiger.config.defaultPort}"
            config:
              defaultPort: "9090"
        """.trimIndent())

        val result = resolvePlaceholders("\${tiger.servers.serverA.serverPort}", config)

        assertThat(result).isEqualTo("9090")
    }

    @Test
    fun `should leave unresolvable placeholders as-is`() {
        val config = globalConfigOf("""
            servers: {}
        """.trimIndent())

        val result = resolvePlaceholders("\${this.one.cant.be.resolved.anymore}", config)

        assertThat(result).isEqualTo("\${this.one.cant.be.resolved.anymore}")
    }

    @Test
    fun `should handle circular references without infinite loop`() {
        val config = globalConfigOf("""
            servers:
              a:
                type: "${'$'}{tiger.servers.b.type}"
              b:
                type: "${'$'}{tiger.servers.a.type}"
        """.trimIndent())

        val result = resolvePlaceholders("\${tiger.servers.a.type}", config)

        assertThat(result).isNotNull()
    }

    @Test
    fun `should return input unchanged when no placeholders present`() {
        val config = globalConfigOf("""
            servers: {}
        """.trimIndent())

        val result = resolvePlaceholders("plain text without placeholders", config)

        assertThat(result).isEqualTo("plain text without placeholders")
    }

    @Test
    fun `should resolve non-tiger root keys from additional config without baseKey`() {
        val mainYaml = """
            additionalConfigurationFiles:
              - filename: external-ports.yaml
            servers:
              myServer:
                type: docker
                serverPort: "${'$'}{config_ports.pop3s.admin}"
        """.trimIndent()
        val additionalYaml = """
            config_ports:
              pop3s:
                admin: "8443"
        """.trimIndent()

        val config = mergeGlobalConfig(listOf(
            UploadedYamlFile("main.yaml", mainYaml),
            UploadedYamlFile("external-ports.yaml", additionalYaml)
        ))

        val result = resolvePlaceholders("\${tiger.servers.myServer.serverPort}", config)

        assertThat(result).isEqualTo("8443")
    }

    @Test
    fun `should resolve mix of tiger and non-tiger placeholders`() {
        val mainYaml = """
            additionalConfigurationFiles:
              - filename: env.yaml
            servers:
              myServer:
                type: docker
                hostname: my-host
        """.trimIndent()
        val envYaml = """
            env:
              defaultPort: "9090"
        """.trimIndent()

        val config = mergeGlobalConfig(listOf(
            UploadedYamlFile("main.yaml", mainYaml),
            UploadedYamlFile("env.yaml", envYaml)
        ))

        val result = resolvePlaceholders(
            "http://\${tiger.servers.myServer.hostname}:\${env.defaultPort}",
            config
        )

        assertThat(result).isEqualTo("http://my-host:9090")
    }
}
