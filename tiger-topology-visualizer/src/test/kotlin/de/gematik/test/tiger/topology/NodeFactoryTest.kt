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

import de.gematik.test.tiger.topology.deserialization.LightConfigServer
import de.gematik.test.tiger.topology.deserialization.LightTigerConfigModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NodeFactoryTest {

    private val identity: ResolvePlaceholders = { it }
    private val noopResolver = YamlResolver { null }

    private fun parseTigerConfig(yaml: String): LightTigerConfigModel =
        yamlMapper.convertValue(
            normalizeMap(parseYamlDocument(yaml)),
            LightTigerConfigModel::class.java
        )

    private fun serverEntry(name: String, yaml: String): Map.Entry<String, LightConfigServer> {
        val config = parseTigerConfig("servers:\n  $name:\n${yaml.prependIndent("    ")}")
        return config.servers.entries.first()
    }


    @Nested
    inner class CreateDiagramNode {

        @Test
        fun `tigerProxy server creates tigerProxy node`() {
            val entry = serverEntry("myProxy", "type: tigerProxy\nhostname: proxy.local")
            val model = createDiagramNode(entry, noopResolver, identity)

            assertThat(model.nodes).anyMatch { it.id.value == "myProxy" && it.type.value == "tigerProxy" }
        }

        @Test
        fun `docker server creates docker node with docker-host parent`() {
            val entry = serverEntry("myDocker", "type: docker\nsource:\n  - my-image:latest")
            val model = createDiagramNode(entry, noopResolver, identity)

            val node = model.nodes.single()
            assertThat(node.id.value).isEqualTo("myDocker")
            assertThat(node.type.value).isEqualTo("docker")
            assertThat(node.parentNode?.value).isEqualTo("docker-host")
        }

        @Test
        fun `externalUrl server creates externalUrl node`() {
            val entry = serverEntry("extSvc", "type: externalUrl\nsource:\n  - https://example.com/api")
            val model = createDiagramNode(entry, noopResolver, identity)

            val node = model.nodes.single()
            assertThat(node.type.value).isEqualTo("externalUrl")
            assertThat(node.data.config["source"]).isEqualTo(listOf("https://example.com/api"))
        }

        @Test
        fun `zion server creates zion node with proxy edge`() {
            val entry = serverEntry("myZion", "type: zion")
            val model = createDiagramNode(entry, noopResolver, identity)

            assertThat(model.nodes).anyMatch { it.id.value == "myZion" && it.type.value == "zion" }
            assertThat(model.edges).containsExactly(
                DiagramEdge.usesProxy(NodeId("myZion"), NodeId("localTigerProxy"))
            )
        }

        @Test
        fun `unknown server type creates node with that type`() {
            val entry = serverEntry("custom", "type: myCustomType")
            val model = createDiagramNode(entry, noopResolver, identity)

            val node = model.nodes.single()
            assertThat(node.type.value).isEqualTo("myCustomType")
            assertThat(node.data.label).isEqualTo("custom")
        }

        @Test
        fun `blank server type defaults to unknown`() {
            val entry = serverEntry("noType", "type: \"\"")
            val model = createDiagramNode(entry, noopResolver, identity)

            assertThat(model.nodes.single().type.value).isEqualTo("unknown")
        }
    }


    @Nested
    inner class TigerProxyNodes {

        @Test
        fun `tiger proxy with routes creates route nodes and edges`() {
            val entry = serverEntry(
                "myProxy", """
                type: tigerProxy
                tigerProxyConfiguration:
                  proxyRoutes:
                    - from: http://frontend
                      to: http://localhost:8080
                    - from: http://backend
                      to: http://localhost:9090
            """.trimIndent()
            )
            val model = createDiagramNode(entry, noopResolver, identity)

            assertThat(model.nodes.map { it.id.value }).contains(
                "myProxy", "myProxy-route-0", "myProxy-route-1"
            )
            assertThat(model.edges.map { it.id.value }).contains(
                "myProxy-to-route-0", "myProxy-to-route-1"
            )
        }


    }


    @Nested
    inner class LocalProxyNode {

        @Test
        fun `creates local proxy node with default hostname`() {
            val config = parseTigerConfig("servers: {}")
            val model = createLocalProxyNode(config)

            val node = model.nodes.single()
            assertThat(node.id.value).isEqualTo("localTigerProxy")
            assertThat(node.type.value).isEqualTo("tigerProxy")
            assertThat(node.data.label).isEqualTo("Local Tiger Proxy")
        }
    }


    @Test
    fun `createDockerHostNode creates a group node`() {
        val model = createDockerHostNode()
        val node = model.nodes.single()
        assertThat(node.id.value).isEqualTo("docker-host")
        assertThat(node.type.value).isEqualTo("group")
    }


    @Nested
    inner class ComposeNodes {

        @Test
        fun `compose server creates group with service children from resolved yaml`() {
            val composeYaml = """
                services:
                  web:
                    image: nginx
                  db:
                    image: postgres
            """.trimIndent()

            val resolver = YamlResolver { composeYaml }
            val entry = serverEntry("myCompose", "type: compose\nsource:\n  - docker-compose.yml")
            val model = createDiagramNode(entry, resolver, identity)

            assertThat(model.nodes.map { it.id.value }).containsExactlyInAnyOrder(
                "myCompose", "myCompose-web", "myCompose-db"
            )
            assertThat(model.nodes.first { it.id.value == "myCompose" }.type.value).isEqualTo("group")
            assertThat(model.nodes.filter { it.id.value.startsWith("myCompose-") })
                .allMatch { it.parentNode?.value == "myCompose" }
        }

        @Test
        fun `compose server with unresolvable file creates group only`() {
            val entry = serverEntry("myCompose", "type: compose\nsource:\n  - missing.yml")
            val model = createDiagramNode(entry, noopResolver, identity)

            assertThat(model.nodes).hasSize(1)
            assertThat(model.nodes.single().id.value).isEqualTo("myCompose")
        }
    }

    @Nested
    inner class ExternalJarNodes {

        @Test
        fun `externalJar with proxy options creates edge to local proxy`() {
            val entry = serverEntry(
                "myJar", """
                type: externalJar
                externalJarOptions:
                  options:
                    - "-Dhttp.proxyHost=127.0.0.1"
                    - "-Dhttp.proxyPort=8888"
            """.trimIndent()
            )
            val model = createDiagramNode(entry, noopResolver, identity)

            assertThat(model.edges).containsExactly(
                DiagramEdge.usesProxy(NodeId("myJar"), NodeId("localTigerProxy"))
            )
        }

        @Test
        fun `externalJar without proxy options creates no proxy edge`() {
            val entry = serverEntry(
                "myJar", """
                type: externalJar
                externalJarOptions:
                  options:
                    - "-Dsome.flag=true"
            """.trimIndent()
            )
            val model = createDiagramNode(entry, noopResolver, identity)

            assertThat(model.edges).isEmpty()
        }
    }


    @Nested
    inner class RouteToServerEdges {

        @Test
        fun `routes are connected to servers by matching serverPort`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  proxyRoutes:
                    - from: http://frontend
                      to: http://localhost:8080
                servers:
                  backend:
                    type: httpbin
                    serverPort: "8080"
                """.trimIndent()
            )

            val model = createRouteToServerEdges(config, ConfigurationDiagramModel.empty(), identity)

            assertThat(model.edges).containsExactly(
                DiagramEdge.routeToTarget(NodeId("localTigerProxy-route-0"), NodeId("backend"))
            )
        }

        @Test
        fun `synthetic node and edge when target does not exist in config`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  proxyRoutes:
                    - from: http://frontend
                      to: http://localhost:9999
                servers:
                  backend:
                    type: externalJar
                    serverPort: "8080"
                """.trimIndent()
            )

            val model = createRouteToServerEdges(config, ConfigurationDiagramModel.empty(), identity)

            assertThat(model.nodes).containsExactly(
                DiagramNode(
                    NodeId("unresolved-local-target-http-localhost-9999"),
                    NodeType.UNRESOLVED_LOCAL_TARGET,
                    NodeData("http://localhost:9999", mapOf("hostname" to "localhost"))
                )
            )
            assertThat(model.edges).containsExactly(
                DiagramEdge.routeToTarget(
                    NodeId("localTigerProxy-route-0"),
                    NodeId("unresolved-local-target-http-localhost-9999")
                )
            )
        }

        @Test
        fun `route to unknown target creates synthetic node`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  proxyRoutes:
                    - from: http://frontend
                      to: http://external-service.com:1234
                """.trimIndent()
            )

            val model = createRouteToServerEdges(config, ConfigurationDiagramModel.empty(), identity)

            assertThat(model.nodes)
                .containsExactly(
                    DiagramNode(
                        id = NodeId("external-backend-http-external-service-com-1234"),
                        type = NodeType.SYNTHETIC_EXTERNAL_URL,
                        data = NodeData("http://external-service.com:1234", mapOf("hostname" to "external-service.com"))
                    )
                )
            assertThat(model.edges).containsExactly(
                DiagramEdge.routeToTarget(
                    NodeId("localTigerProxy-route-0"),
                    NodeId("external-backend-http-external-service-com-1234")
                )
            )
        }

        @Test
        fun `route to already matched target does not create synthetic node`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  proxyRoutes:
                    - from: http://frontend
                      to: http://already-matched:1234
                """.trimIndent()
            )
            val existingModel = ConfigurationDiagramModel.fromNodes(
                listOf(
                    DiagramNode(
                        NodeId("matched-node"),
                        NodeType.DOCKER,
                        NodeData("already-matched", mapOf("hostname" to "already-matched"))
                    )
                )
            )

            val model = createRouteToServerEdges(config, existingModel, identity)

            assertThat(model.nodes).isEmpty()
            assertThat(model.edges).isEmpty() // matched by Docker/Compose elsewhere, so this one should be silent
        }

        @Test
        fun `route to externalUrl server via source URL host creates edge`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  proxyRoutes:
                    - from: /example
                      to: https://www.example.com
                servers:
                  exampleCom:
                    type: externalUrl
                    source:
                      - https://www.example.com
                """.trimIndent()
            )


            val model = createRouteToServerEdges(
                config,
                createDiagramNode(config.servers.entries.first(), noopResolver, identity),
                identity
            )

            assertThat(model.edges).hasSize(1)
            assertThat(model.edges[0].target).isEqualTo(NodeId("exampleCom"))
        }
    }


    @Nested
    inner class RouteToDockerEdges {

        @Test
        fun `routes are connected to docker containers by matching host port`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  proxyRoutes:
                    - from: http://mycontainer
                      to: http://localhost:8080
                servers:
                  mycontainer:
                    type: docker
                    dockerOptions:
                      ports:
                        - "8080:80"
                """.trimIndent()
            )

            val model = createRouteToDockerEdges(config, identity)

            assertThat(model.edges).containsExactly(
                DiagramEdge.routeToTarget(NodeId("localTigerProxy-route-0"), NodeId("mycontainer"))
            )
        }
    }

    @Nested
    inner class ImplicitProxyEdges {

        @Test
        fun `implicit edges created for httpbin, externalJar, externalUrl, zion when proxy active`() {
            val config = parseTigerConfig(
                """
                localProxyActive: true
                servers:
                  svc1:
                    type: externalUrl
                  svc2:
                    type: zion
                  svc3:
                    type: docker
                """.trimIndent()
            )

            val model = createImplicitProxyToServerEdges(config)

            assertThat(model.edges.map { it.target.value }).containsExactlyInAnyOrder("svc1", "svc2")
            assertThat(model.edges).noneMatch { it.target.value == "svc3" }
        }

        @Test
        fun `no implicit edges when proxy is inactive`() {
            val config = parseTigerConfig(
                """
                localProxyActive: false
                servers:
                  svc1:
                    type: externalUrl
                """.trimIndent()
            )

            val model = createImplicitProxyToServerEdges(config)

            assertThat(model.edges).isEmpty()
        }
    }


    @Nested
    inner class TrafficEndpointEdges {

        @Test
        fun `traffic endpoints connect local proxy to remote proxies by admin port`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  trafficEndpoints:
                    - http://localhost:9090
                servers:
                  remoteProxy:
                    type: tigerProxy
                    tigerProxyConfiguration:
                      adminPort: "9090"
                """.trimIndent()
            )

            val model = createTrafficEndpointEdges(config, identity)

            assertThat(model.nodes).hasSize(0)
            assertThat(model.edges).containsExactly(
                DiagramEdge.trafficSubscription(NodeId("localTigerProxy"), NodeId("remoteProxy"))
            )
        }

        @Test
        fun `external proxy edge when endpoint not in configuration`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  trafficEndpoints:
                    - http://localhost:7777
                servers:
                  remoteProxy:
                    type: tigerProxy
                    tigerProxyConfiguration:
                      adminPort: "9090"
                """.trimIndent()
            )

            val model = createTrafficEndpointEdges(config, identity)

            //when the endpoint does not exist in tigeryaml we create a synthetic node for it to reprsent
            //an external tiger proxy
            assertThat(model.nodes).hasSize(1)
            assertThat(model.nodes.single().type).isEqualTo(NodeType.TIGER_PROXY_EXTERNAL)
            assertThat(model.edges).containsExactly(
                DiagramEdge.trafficSubscription(NodeId("localTigerProxy"), NodeId("http-localhost-7777"))
            )
        }

        @Test
        fun `traffic endpoints match when admin port is an integer in YAML`() {
            val config = parseTigerConfig(
                """
                tigerProxy:
                  trafficEndpoints:
                    - http://localhost:123
                servers:
                  remoteProxy:
                    type: tigerProxy
                    tigerProxyConfiguration:
                      adminPort: 123
                """.trimIndent()
            )

            val model = createTrafficEndpointEdges(config, identity)

            assertThat(model.nodes).hasSize(0)
            assertThat(model.edges).containsExactly(
                DiagramEdge.trafficSubscription(NodeId("localTigerProxy"), NodeId("remoteProxy"))
            )

        }

        @Test
        fun `traffic endpoints chain of proxies create edges between all involved proxies`() {
            val config = parseTigerConfig(
                """
                tigerProxy:  
                  trafficEndpoints:
                    - http://localhost:123
                servers:
                  proxyA:
                    type: tigerProxy
                    tigerProxyConfiguration:
                      adminPort: 123
                      trafficEndpoints:
                        - http://localhost:456
                  proxyB:
                    type: tigerProxy
                    tigerProxyConfiguration:
                      adminPort: 456
                """.trimIndent()
            )

            val model = createTrafficEndpointEdges(config, identity)

            //2 Edges
            // tigerProxy -- subscribes --> proxyA
            // proxyA -- subscribes --> proxyB
            assertThat(model.edges).hasSize(2)

            assertThat(model.edges).containsExactlyInAnyOrder(
                DiagramEdge.trafficSubscription(NodeId(LOCAL_PROXY), NodeId("proxyA")),
                DiagramEdge.trafficSubscription(NodeId("proxyA"), NodeId("proxyB")),
            )
        }
    }


    @Nested
    inner class ZionBackendEdges {

        @Test
        fun `zion backend request URLs create edges to matching nodes`() {
            val config = parseTigerConfig(
                """
                servers:
                  myZion:
                    type: zion
                    zionConfiguration:
                      mockResponses:
                        resp1:
                          backendRequests:
                            req1:
                              url: http://backend.local/api
                  backendServer:
                    type: externalUrl
                    source:
                      - http://backend.local/api
                """.trimIndent()
            )

            val existingModel = ConfigurationDiagramModel(
                nodes = listOf(
                    DiagramNode(
                        NodeId("backendServer"),
                        NodeType.EXTERNAL_URL,
                        NodeData("backendServer", mapOf("hostname" to "backend.local"))
                    )
                ),
                edges = emptyList()
            )

            val model = createZionBackendEdges(config, existingModel, identity)

            assertThat(model.edges).containsExactly(
                DiagramEdge.makesBackendRequest(NodeId("myZion"), NodeId("backendServer"), NodeId(LOCAL_PROXY))
            )
        }

        @Test
        fun `zion backend request to unknown URL creates synthetic external node`() {
            val config = parseTigerConfig(
                """
                servers:
                  myZion:
                    type: zion
                    zionConfiguration:
                      mockResponses:
                        resp1:
                          backendRequests:
                            req1:
                              url: http://unknown.example.com/api
                """.trimIndent()
            )

            val model = createZionBackendEdges(config, ConfigurationDiagramModel.empty(), identity)


            assertThat(model.nodes).containsExactly(
                DiagramNode(
                    id = NodeId("external-backend-http-unknown-example-com-api"),
                    type = NodeType.SYNTHETIC_EXTERNAL_URL,
                    data = NodeData("http://unknown.example.com/api", mapOf("hostname" to "unknown.example.com"))
                )
            )
            assertThat(model.edges).containsExactly(
                DiagramEdge.makesBackendRequest(
                    NodeId("myZion"),
                    NodeId("external-backend-http-unknown-example-com-api"),
                    NodeId(LOCAL_PROXY)
                )
            )
        }
    }
}

