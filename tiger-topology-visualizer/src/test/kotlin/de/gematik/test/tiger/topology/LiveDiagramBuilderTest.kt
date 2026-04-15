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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LiveDiagramBuilderTest {

    @BeforeEach
    fun setUp() {
        TigerGlobalConfiguration.reset()
    }

    @Test
    fun `should return empty diagram when TigerGlobalConfiguration is not initialized`() {
        TigerGlobalConfiguration.reset()

        val diagram = buildDiagramFromLiveConfiguration()

        assertThat(diagram.nodes).isEmpty()
        assertThat(diagram.edges).isEmpty()
    }

    @Test
    fun `should build diagram from live configuration with servers`() {
        TigerGlobalConfiguration.reset()
        TigerGlobalConfiguration.initialize()
        TigerGlobalConfiguration.readFromYaml("""
            servers:
              myDocker:
                type: docker
                source:
                  - some:image
                dockerOptions:
                  ports:
                    - "8080:80"
              myZion:
                type: zion
                zionConfiguration:
                  serverPort: "9090"
        """.trimIndent(), "tiger")

        val diagram = buildDiagramFromLiveConfiguration()

        assertThat(diagram.nodes.map { it.id.value }).contains("localTigerProxy", "docker-host", "myDocker", "myZion")
        assertThat(diagram.nodes.find { it.id.value == "myDocker" }?.type?.value).isEqualTo("docker")
        assertThat(diagram.nodes.find { it.id.value == "myZion" }?.type?.value).isEqualTo("zion")
    }

    @Test
    fun `should build diagram with local proxy disabled`() {
        TigerGlobalConfiguration.initialize()
        TigerGlobalConfiguration.readFromYaml("""
            localProxyActive: false
            servers:
              myService:
                type: externalUrl
                source:
                  - https://example.test/api
        """.trimIndent(), "tiger")

        val diagram = buildDiagramFromLiveConfiguration()

        assertThat(diagram.nodes.map { it.id.value }).contains("myService")
        assertThat(diagram.nodes.map { it.id.value }).doesNotContain("localTigerProxy")
    }

    @Test
    fun `should resolve placeholders from live configuration`() {
        TigerGlobalConfiguration.reset()
        TigerGlobalConfiguration.initialize()
        TigerGlobalConfiguration.readFromYaml("""
            config:
              adminPort: "7070"
            tigerProxy:
              trafficEndpoints:
                - "http://localhost:7070"
            servers:
              remoteProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: "${'$'}{tiger.config.adminPort}"
        """.trimIndent(), "tiger")

        val diagram = buildDiagramFromLiveConfiguration()

        assertThat(diagram.edges.map { it.id.value }).contains("localTigerProxy-to-remoteProxy-trafficEndpoint")
    }

    @Test
    fun `should return empty diagram when no servers are configured`() {
        TigerGlobalConfiguration.reset()
        TigerGlobalConfiguration.initialize()

        val diagram = buildDiagramFromLiveConfiguration()

        // Only local proxy node when localProxyActive defaults to true
        assertThat(diagram.nodes.map { it.id.value }).contains("localTigerProxy")
        assertThat(diagram.edges).isEmpty()
    }
}




