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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class YamlToDiagramConverterTest {

    @Test
    fun `multiple uploaded topology files should be combined into one diagram`() {
        val uploadedFiles = listOf(
            UploadedYamlFile(
                "routes.yaml", """
                tigerProxy:
                  proxyRoutes:
                    - from: http://dockerA
                      to: http://host.docker.internal:8080
                additionalConfigurationFiles:
                  - filename: external.yaml
                    baseKey: tiger
                servers:
                  dockerA:
                    type: docker
                    source:
                      - some:image
                    dockerOptions:
                      ports:
                        - "8080:80"
                """.trimIndent()
            ),
            UploadedYamlFile(
                "external.yaml", """
                servers:
                  publicService:
                    type: externalUrl
                    source:
                      - https://example.test/service
                """.trimIndent()
            )
        )

        val diagramModel = convertUploadedFilesToDiagramModel(uploadedFiles)

        assertThat(diagramModel.nodes.map { it.id.value }).contains(
            "localTigerProxy",
            "docker-host",
            "dockerA",
            "publicService",
            "localTigerProxy-route-0"
        )
        assertThat(diagramModel.edges.map { it.id.value }).contains("localTigerProxy-route-0-to-dockerA")
    }

    @Test
    fun `compose file references should be resolved by uploaded file name`() {
        val topologyYaml = """
            localProxyActive: false
            servers:
              dockerComposeTest:
                type: compose
                source:
                  - C:/tmp/project/config/docker-compose-test.yaml
        """.trimIndent()
        val composeYaml = """
            version: '3'
            services:
              alpha:
                image: example/alpha
              beta:
                image: example/beta
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(
            listOf(
                UploadedYamlFile("topology.yaml", topologyYaml),
                UploadedYamlFile("docker-compose-test.yaml", composeYaml)
            )
        )

        assertThat(diagramModel.nodes.filter { it.parentNode?.value == "dockerComposeTest" }.map { it.data.label })
            .containsExactlyInAnyOrder("alpha", "beta")
    }

    @Test
    fun `diagram should still be generated when referenced compose upload is removed`() {
        val topologyYaml = """
            localProxyActive: false
            servers:
              dockerComposeTest:
                type: compose
                source:
                  - C:/tmp/project/config/docker-compose-test.yaml
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(
            listOf(UploadedYamlFile("topology.yaml", topologyYaml))
        )

        assertThat(diagramModel.nodes.map { it.id.value }).contains("docker-host", "dockerComposeTest")
        assertThat(diagramModel.nodes.map { it.type.value }).doesNotContain("composeService")
    }

    // ── Warnings for missing files ──────────────────────────────────────────

    @Test
    fun `missing additional configuration file should produce a warning`() {
        val mainYaml = """
            additionalConfigurationFiles:
              - filename: C:/tmp/missing-file.yaml
                baseKey: tiger
            servers: {}
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(
            listOf(UploadedYamlFile("main.yaml", mainYaml))
        )

        assertThat(diagramModel.warnings).hasSize(1)
        assertThat(diagramModel.warnings.first()).contains("missing-file.yaml")
    }

    @Test
    fun `no warnings when all additional configuration files are present`() {
        val mainYaml = """
            additionalConfigurationFiles:
              - filename: extra.yaml
                baseKey: tiger
            servers: {}
        """.trimIndent()
        val extraYaml = """
            servers:
              myService:
                type: externalUrl
                source:
                  - https://example.test
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(
            listOf(
                UploadedYamlFile("main.yaml", mainYaml),
                UploadedYamlFile("extra.yaml", extraYaml)
            )
        )

        assertThat(diagramModel.warnings).isEmpty()
    }

    @Test
    fun `multiple missing additional files should produce multiple warnings`() {
        val mainYaml = """
            additionalConfigurationFiles:
              - filename: missing-a.yaml
                baseKey: tiger
              - filename: missing-b.yaml
                baseKey: tiger
            servers: {}
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(
            listOf(UploadedYamlFile("main.yaml", mainYaml))
        )

        assertThat(diagramModel.warnings).hasSize(2)
        assertThat(diagramModel.warnings).anyMatch { it.contains("missing-a.yaml") }
        assertThat(diagramModel.warnings).anyMatch { it.contains("missing-b.yaml") }
    }

    @Test
    fun `additional configuration files with dbase key tiger should extend the topology`() {
        val mainYaml = """
            additionalConfigurationFiles:
              - filename: C:/tmp/project/config/additional-topology.yaml
                baseKey: tiger
            servers: {}
        """.trimIndent()
        val additionalYaml = """
            servers:
              externalService:
                type: externalUrl
                source:
                  - https://example.test/additional
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(
            listOf(
                UploadedYamlFile("main-topology.yaml", mainYaml),
                UploadedYamlFile("additional-topology.yaml", additionalYaml)
            )
        )

        assertThat(diagramModel.nodes.map { it.id.value }).contains("localTigerProxy", "externalService")
    }

    @Test
    fun `additional configuration files without base key should merge tiger rooted YAML`() {
        val mainYaml = """
            additionalConfigurationFiles:
              - filename: nested/external-with-tiger.yaml
        """.trimIndent()
        val additionalYaml = """
            tiger:
              localProxyActive: false
              servers:
                tigerScopedService:
                  type: externalUrl
                  source:
                    - https://example.test/tiger-scoped
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(
            listOf(
                UploadedYamlFile("main-topology.yaml", mainYaml),
                UploadedYamlFile("external-with-tiger.yaml", additionalYaml)
            )
        )

        assertThat(diagramModel.nodes.map { it.id.value }).contains("tigerScopedService")
        assertThat(diagramModel.nodes.map { it.id.value }).doesNotContain("localTigerProxy")
    }

    @Test
    fun `minimal yaml should create local proxy node`() {
        val yaml = """
                tigerProxy:
                servers: {}
                """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.nodes).hasSize(1)
        assertThat(diagramModel.nodes.get(0).id.value).isEqualTo("localTigerProxy")
        assertThat(diagramModel.nodes.get(0).data.label).isEqualTo("Local Tiger Proxy")
        assertThat(diagramModel.edges).hasSize(0)
    }

    @Test
    fun `should create docker host parent node with docker child nodes`() {
        val yaml = """
            localProxyActive: false
            servers:
              dockerA:
                type: docker
                source:
                  - some:image
                version: latest
                dockerOptions:
                  ports:
                    - "8080:80"
              dockerB:
                type: docker
                source:
                  - some:image
                version: latest
                dockerOptions:
                  ports:
                    - "8085:80"
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        // Should have docker-host parent node
        val dockerHostNode = diagramModel.nodes.find { it.id.value == "docker-host" }
        assertThat(dockerHostNode?.id?.value).isEqualTo("docker-host")
        assertThat(dockerHostNode?.type?.value).isEqualTo("group")

        // Should have two docker child nodes
        val dockerChildren = diagramModel.nodes.filter {
            it.parentNode?.value == "docker-host"
        }
        assertThat(dockerChildren).hasSize(2)

        // Check dockerA node
        val dockerANode = dockerChildren.find { it.id.value == "dockerA" }
        assertThat(dockerANode?.id?.value).isEqualTo("dockerA")
        assertThat(dockerANode?.type?.value).isEqualTo("docker")
        assertThat(dockerANode?.parentNode?.value).isEqualTo("docker-host")

        // Check dockerB node
        val dockerBNode = dockerChildren.find { it.id.value == "dockerB" }
        assertThat(dockerBNode?.id?.value).isEqualTo("dockerB")
        assertThat(dockerBNode?.type?.value).isEqualTo("docker")
        assertThat(dockerBNode?.parentNode?.value).isEqualTo("docker-host")
    }

    @Test
    fun `should create nodes for tiger proxy routes`() {
        val yaml = """
          tigerProxy:
            proxyRoutes:
              - from: "http://my.domain"
                to: "http://orf.at"
          servers: { }
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.nodes)
            .usingRecursiveComparison()
            .ignoringFields("data")
            .isEqualTo(listOf(
                DiagramNode(id = NodeId("localTigerProxy"), type = NodeType.TIGER_PROXY, NodeData("")),
                DiagramNode(id = NodeId("localTigerProxy-route-0"), type = NodeType.ROUTE, NodeData("")),
                DiagramNode(id = NodeId("external-backend-http-orf-at"), type = NodeType.SYNTHETIC_EXTERNAL_URL, NodeData("")),
            ))
        assertThat(diagramModel.nodes.map { it.data.label }).containsExactly(
            "Local Tiger Proxy",
            "http://my.domain",
            "http://orf.at"
        )
        assertThat(diagramModel.edges).containsExactly(
            DiagramEdge.proxyToRoute("localTigerProxy", 0, source= NodeId("localTigerProxy"), target=NodeId("localTigerProxy-route-0")),
            DiagramEdge.routeToTarget(NodeId("localTigerProxy-route-0"), NodeId("external-backend-http-orf-at"))
        )
    }

    @Test
    fun `should connect traffic endpoints to tiger proxy servers via placeholder ports`() {
        val yaml = """
            config_ports:
              pop3s:
                admin: ${'$'}{free.port.0}
              smtps:
                admin: ${'$'}{free.port.4}
            tigerProxy:
              trafficEndpoints:
                - http://localhost:${'$'}{tiger.config_ports.pop3s.admin}
                - http://localhost:${'$'}{tiger.config_ports.smtps.admin}
            servers:
              smtpsProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${'$'}{tiger.config_ports.smtps.admin}
              pop3sProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${'$'}{tiger.config_ports.pop3s.admin}
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("tiger-mesh.yaml", yaml)))

        assertThat(diagramModel.edges.map { it.id.value }).contains(
            "localTigerProxy-to-smtpsProxy-trafficEndpoint",
            "localTigerProxy-to-pop3sProxy-trafficEndpoint"
        )
    }

    // ── External JAR ────────────────────────────────────────────────────────

    @Test
    fun `externalJar with proxy options should create combined uses-proxy edge -and- implicit route`() {
        val yaml = $$"""
            servers:
              idpServer:
                type: externalJar
                source:
                  - target/idp-server.jar
                externalJarOptions:
                  options:
                    - -Dhttp.proxyHost=127.0.0.1
                    - -Dhttp.proxyPort=${tiger.tigerProxy.proxyPort}
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.nodes.map { it.id.value }).contains("localTigerProxy", "idpServer")
        assertThat(diagramModel.nodes.find { it.id.value == "idpServer" }?.type?.value).isEqualTo("externalJar")
        assertThat(diagramModel.edges.map { it.id.value }).contains("idpServer-to-localTigerProxy-and-localTigerProxy-to-idpServer-implicit")
        assertThat(diagramModel.edges.find { it.id.value == "idpServer-to-localTigerProxy-and-localTigerProxy-to-idpServer-implicit" }?.label).isEqualTo(
            "uses proxy / automatic route to"
        )
    }

    @Test
    fun `externalJar without proxy options should not create uses-proxy edge`() {
        val yaml = """
            servers:
              standaloneApp:
                type: externalJar
                source:
                  - target/app.jar
                externalJarOptions:
                  options:
                    - -Dserver.port=9090
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.nodes.map { it.id.value }).contains("standaloneApp")
        assertThat(diagramModel.edges.map { it.id.value }).doesNotContain("standaloneApp-to-localTigerProxy")
    }


    @Test
    fun `zion server should create combined uses-proxy edge -and- implicit route`() {
        val yaml = """
            servers:
              mockServer:
                type: zion
                zionConfiguration:
                  serverPort: "8080"
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.nodes.map { it.id.value }).contains("localTigerProxy", "mockServer")
        assertThat(diagramModel.nodes.find { it.id.value == "mockServer" }?.type?.value).isEqualTo("zion")
        assertThat(diagramModel.edges.map { it.id.value }).contains("mockServer-to-localTigerProxy-and-localTigerProxy-to-mockServer-implicit")
        assertThat(diagramModel.edges.find { it.id.value == "mockServer-to-localTigerProxy-and-localTigerProxy-to-mockServer-implicit" }?.label).isEqualTo(
            "uses proxy / automatic route to"
        )
    }

    @Test
    fun `zion backend requests should create edges to matching server nodes`() {
        val yaml = """
            servers:
              mockServer:
                type: zion
                zionConfiguration:
                  mockResponses:
                    getPatient:
                      backendRequests:
                        fetchData:
                          url: http://realBackend:8080/api/patient
              realBackend:
                type: externalUrl
                source:
                  - http://realBackend:8080
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.edges.filter { it.label == "makes backend request" }).isNotEmpty
        assertThat(diagramModel.edges.map { it.id.value })
            .contains("mockServer-to-realBackend-backendRequest")
    }

    @Test
    fun `zion backend request to unknown URL should create synthetic external node`() {
        val yaml = """
            localProxyActive: false
            servers:
              mockServer:
                type: zion
                zionConfiguration:
                  mockResponses:
                    lookup:
                      backendRequests:
                        callExternal:
                          url: https://unknown-service.example.com/api
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        val syntheticNode = diagramModel.nodes.find { it.data.config["hostname"] == "unknown-service.example.com" }
        assertThat(syntheticNode).isNotNull
        assertThat(syntheticNode!!.type.value).isEqualTo("syntheticExternalUrl")
        assertThat(diagramModel.edges.any { it.source == NodeId("mockServer") && it.target == syntheticNode.id }).isTrue()
    }

    // ── Reverse proxy ───────────────────────────────────────────────────────

    @Test
    fun `tiger proxy with directReverseProxy should create reverse target node and edge`() {
        val yaml = """
            localProxyActive: false
            servers:
              myProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: "9090"
                  directReverseProxy:
                    hostname: 127.0.0.1
                    port: "3000"
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        // Should have the reverse target node
        val reverseNode = diagramModel.nodes.find { it.id.value == "myProxy-directReverseTarget" }
        assertThat(reverseNode).isNotNull
        assertThat(reverseNode!!.type.value).isEqualTo("directReverseTarget")
        assertThat(reverseNode.data.config["hostname"]).isEqualTo("127.0.0.1")
        assertThat(reverseNode.data.config["port"]).isEqualTo("3000")

        // Should have the edge from proxy to reverse target
        val reverseEdge =
            diagramModel.edges.find { it.id.value == "myProxy-to-myProxy-directReverseTarget-directReverse" }
        assertThat(reverseEdge).isNotNull
        assertThat(reverseEdge!!.source).isEqualTo(NodeId("myProxy"))
        assertThat(reverseEdge.target).isEqualTo(NodeId("myProxy-directReverseTarget"))
        assertThat(reverseEdge.label).isEqualTo("direct reverse")
    }

    @Test
    fun `tiger proxy without directReverseProxy should not create reverse target`() {
        val yaml = """
            localProxyActive: false
            servers:
              simpleProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: "9090"
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.nodes.map { it.id.value }).contains("simpleProxy")
        assertThat(diagramModel.nodes.map { it.id.value }).doesNotContain("simpleProxy-directReverseTarget")
        assertThat(diagramModel.edges.map { it.id.value }).doesNotContain("simpleProxy-to-directReverseTarget")
    }

    // ── Route-to-server matching ─────────────────────────────────────────────

    @Test
    fun `route to URL should create edge to server with matching serverPort`() {
        val yaml = """
            servers:
              httpbin:
                type: httpbin
                serverPort: 8080
              remoteTigerProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: "9090"
                  proxyRoutes:
                    - from: http://httpbin
                      to: http://localhost:8080
                    - from: /httpbin
                      to: http://localhost:8080
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.edges.map { it.id.value }).contains("remoteTigerProxy-route-0-to-httpbin")
        assertThat(diagramModel.edges.map { it.id.value }).contains("remoteTigerProxy-route-1-to-httpbin")
    }

    @Test
    fun `route to URL with placeholder port should match server with same placeholder serverPort`() {
        val yaml = """
            servers:
              httpbin:
                type: httpbin
                serverPort: ${'$'}{free.port.4}
              remoteTigerProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: "9090"
                  proxyRoutes:
                    - from: http://httpbin
                      to: http://localhost:${'$'}{free.port.4}
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        // Placeholders resolve to the same string, so the ports match
        assertThat(diagramModel.edges.map { it.id.value }).contains("remoteTigerProxy-route-0-to-httpbin")
    }

    @Test
    fun `routes should match targets by server name or by serverPort`() {
        val yaml = """
        tigerProxy:
          trafficEndpoints:
            - http://localhost:${'$'}{free.port.2}
            - http://localhost:${'$'}{free.port.4}
            - http://localhost:${'$'}{free.port.6}
        servers:
          serverUnderTest:
            type: externalUrl
            source:
              - https://www.example.com
          zionA:
            type: zion
            zionConfiguration:
              serverPort: ${'$'}{free.port.1}
          proxyA:
            type: tigerProxy
            tigerProxyConfiguration:
              adminPort: ${'$'}{free.port.2}
              proxyPort: 8081
              rewriteHostHeader: true
              proxyRoutes:
                - from: /
                  to: http://localhost:${'$'}{free.port.1}
          zionB:
            type: zion
            zionConfiguration:
              serverPort: ${'$'}{free.port.3}
          proxyB:
            type: tigerProxy
            tigerProxyConfiguration:
              adminPort: ${'$'}{free.port.4}
              proxyPort: 8082
              rewriteHostHeader: true
              proxyRoutes:
                - from: /
                  to: http://localhost:${'$'}{free.port.3}
          zionC:
            type: zion
            zionConfiguration:
              serverPort: ${'$'}{free.port.5}
          proxyC:
            type: tigerProxy
            tigerProxyConfiguration:
              adminPort: ${'$'}{free.port.6}
              proxyPort: 8083
              proxyRoutes:
                - from: /
                  to: http://localhost:${'$'}{free.port.5}
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.nodes).hasSize(11)

        assertThat(diagramModel.edges.map { it.id.value })
            .containsExactlyInAnyOrder(
                "localTigerProxy-to-proxyA-trafficEndpoint",
                "proxyA-to-route-0",
                "proxyA-route-0-to-zionA",
                "zionA-to-localTigerProxy-and-localTigerProxy-to-zionA-implicit",
                "localTigerProxy-to-proxyB-trafficEndpoint",
                "proxyB-to-route-0",
                "proxyB-route-0-to-zionB",
                "zionB-to-localTigerProxy-and-localTigerProxy-to-zionB-implicit",
                "localTigerProxy-to-proxyC-trafficEndpoint",
                "proxyC-to-route-0",
                "proxyC-route-0-to-zionC",
                "zionC-to-localTigerProxy-and-localTigerProxy-to-zionC-implicit",
                "localTigerProxy-to-serverUnderTest-implicit",
            )
    }

    @Test
    fun `httpbin server should have implicit route edge from local proxy`() {
        val yaml = """
            servers:
              httpbin:
                type: httpbin
                serverPort: 8080
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.edges.map { it.id.value }).contains("localTigerProxy-to-httpbin-implicit")
        val edge = diagramModel.edges.find { it.id.value == "localTigerProxy-to-httpbin-implicit" }!!
        assertThat(edge.source).isEqualTo(NodeId("localTigerProxy"))
        assertThat(edge.target).isEqualTo(NodeId("httpbin"))
        assertThat(edge.label).isEqualTo("automatic route to")
    }

    @Test
    fun `implicit route edges should not be created when localProxyActive is false`() {
        val yaml = """
            localProxyActive: false
            servers:
              httpbin:
                type: httpbin
                serverPort: 8080
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("test.yaml", yaml)))

        assertThat(diagramModel.edges.map { it.id.value }).doesNotContain("localTigerProxy-implicit-to-httpbin")
    }

    @Test
    fun `full httpbin example should create route-to-server and implicit edges`() {
        val yaml = """
            tigerProxy:
              proxyPort: ${'$'}{free.port.3}
              trafficEndpoints:
                - http://localhost:${'$'}{free.port.2}
            servers:
              httpbin:
                type: httpbin
                serverPort: ${'$'}{free.port.4}
                healthcheckUrl: http://localhost:${'$'}{free.port.4}/status/200
              remoteTigerProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${'$'}{free.port.2}
                  proxyPort: ${'$'}{free.port.1}
                  proxyRoutes:
                    - from: http://httpbin
                      to: http://localhost:${'$'}{free.port.4}
                    - from: /httpbin
                      to: http://localhost:${'$'}{free.port.4}
        """.trimIndent()

        val diagramModel = convertUploadedFilesToDiagramModel(listOf(UploadedYamlFile("tiger-httpbin.yaml", yaml)))

        // Nodes
        assertThat(diagramModel.nodes.map { it.id.value }).containsAll(
            listOf(
                "localTigerProxy", "httpbin", "remoteTigerProxy",
                "remoteTigerProxy-route-0", "remoteTigerProxy-route-1"
            )
        )

        // Traffic endpoint: local proxy → remote tiger proxy
        assertThat(diagramModel.edges.map { it.id.value }).contains("localTigerProxy-to-remoteTigerProxy-trafficEndpoint")

        // Route-to-server: both routes point to httpbin (matching serverPort)
        assertThat(diagramModel.edges.map { it.id.value }).contains("remoteTigerProxy-route-0-to-httpbin")
        assertThat(diagramModel.edges.map { it.id.value }).contains("remoteTigerProxy-route-1-to-httpbin")

        // Implicit: local proxy → httpbin (httpbin type auto-registers route)
        //TODO: Currently not active - will decide later if good idea to add implicit edges
        //assertThat(diagramModel.edges.map { it.id.value }).contains("localTigerProxy-implicit-to-httpbin")
    }
}
