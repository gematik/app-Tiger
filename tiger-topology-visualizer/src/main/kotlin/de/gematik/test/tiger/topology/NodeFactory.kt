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
import de.gematik.test.tiger.topology.deserialization.LightRoute
import de.gematik.test.tiger.topology.deserialization.LightTigerConfigModel
import de.gematik.test.tiger.topology.util.extractPortFromUrl
import java.net.URI

// ──────────────────────────────────────────────────────────────────────────────
// Pure functions that build diagram nodes and edges from a LightTigerConfigModel.
//
// Every function takes data in, returns a ConfigurationDiagramModel fragment.
// The optional [resolve] lambda resolves ${…} placeholders before comparisons.
// ──────────────────────────────────────────────────────────────────────────────

/** Convenience typealias for placeholder resolution. Identity (`{ it }`) when no resolver is available. */
internal typealias Resolve = (String) -> String

private const val DOCKER_HOST = "docker-host"
private const val LOCAL_PROXY = "localTigerProxy"


// ── Server dispatch ─────────────────────────────────────────────────────────

internal fun createDiagramNode(
    server: Map.Entry<String, LightConfigServer>,
    uploadedYaml: Map<String, String>,
    resolve: Resolve
): ConfigurationDiagramModel = when (server.value.type) {
    "tigerProxy" -> createTigerProxyNode(server)
    "docker" -> createDockerNode(server)
    "compose" -> createComposeNode(server, uploadedYaml)
    "externalJar" -> createExternalJarNode(server, resolve)
    "externalUrl" -> createExternalUrlNode(server)
    "zion" -> createZionNode(server)
    else -> DiagramNode(
        id = NodeId(server.key),
        type = NodeType(server.value.type.ifBlank { "unknown" }),
        data = NodeData(mapOf("label" to server.key))
    ).singletonModel()
}


// ── Tiger proxy ─────────────────────────────────────────────────────────────

internal fun createLocalProxyNode(config: LightTigerConfigModel) = DiagramNode(
    id = NodeId(LOCAL_PROXY),
    type = NodeType("tigerProxy"),
    data = NodeData(buildMap {
        put("label", "Local Tiger Proxy")
        put("host", config.tigerProxy?.get("hostname") ?: "localhost")
        put("additionalFields", config.tigerProxy?.additionalFields ?: emptyMap<String, Any?>())
    })
).singletonModel()

private fun createTigerProxyNode(server: Map.Entry<String, LightConfigServer>): ConfigurationDiagramModel {
    val node = DiagramNode(
        id = NodeId(server.key),
        type = NodeType("tigerProxy"),
        data = NodeData(
            mapOf<String, Any>(
                "label" to server.key,
                "host" to server.value.hostname.ifBlank { server.key },
                "additionalFields" to server.value.additionalFields
            )
        )
    )
    return node.singletonModel() +
            createRouteNodesAndEdges(server.key, server.value.tigerProxyConfiguration?.proxyRoutes.orEmpty()) +
            createReverseProxyNodeAndEdge(server)
}

private fun createReverseProxyNodeAndEdge(server: Map.Entry<String, LightConfigServer>): ConfigurationDiagramModel {
    val reverse = server.value.tigerProxyConfiguration?.directReverseProxy
    val host = reverse?.hostname?.takeIf { it.isNotBlank() } ?: return ConfigurationDiagramModel.empty()
    val port = reverse.port?.takeIf { it.isNotBlank() } ?: return ConfigurationDiagramModel.empty()

    val nodeId = NodeId("${server.key}-directReverseTarget")
    return DiagramNode(
        id = nodeId,
        type = NodeType("directReverseTarget"),
        data = NodeData(mapOf("label" to "$host:$port", "hostname" to host, "port" to port))
    ).singletonModel() + DiagramEdge(
        id = EdgeId("${server.key}-to-directReverseTarget"),
        source = NodeId(server.key),
        target = nodeId,
        label = "direct reverse"
    ).singletonModel()
}


// ── Routes ──────────────────────────────────────────────────────────────────

internal fun createRouteNodesAndEdges(proxyName: String, routes: List<LightRoute>): ConfigurationDiagramModel {
    val nodes = mutableListOf<DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()

    routes.forEachIndexed { i, route ->
        val id = NodeId("$proxyName-route-$i")
        nodes += DiagramNode(id, NodeType("route"), NodeData(mapOf("label" to route.from, "additionalFields" to route)))
        edges += DiagramEdge(EdgeId("$proxyName-to-route-$i"), NodeId(proxyName), id)
    }
    return ConfigurationDiagramModel(nodes, edges)
}


// ── Docker ──────────────────────────────────────────────────────────────────

internal fun createDockerHostNode() = DiagramNode(
    id = NodeId(DOCKER_HOST),
    type = NodeType("group"),
    data = NodeData(mapOf("label" to "docker host"))
).singletonModel()

private fun createDockerNode(server: Map.Entry<String, LightConfigServer>) = DiagramNode(
    id = NodeId(server.key),
    type = NodeType("docker"),
    data = NodeData(mapOf("label" to server.key, "additionalFields" to server.value.additionalFields)),
    parentNode = NodeId(DOCKER_HOST),
    expandParent = true
).singletonModel()

private fun createComposeNode(
    server: Map.Entry<String, LightConfigServer>,
    uploadedYaml: Map<String, String>
): ConfigurationDiagramModel {
    val groupNode = DiagramNode(
        id = NodeId(server.key),
        type = NodeType("group"),
        data = NodeData(mapOf("label" to server.key, "additionalFields" to server.value.additionalFields)),
        parentNode = NodeId(DOCKER_HOST),
        expandParent = true
    )
    val composeFilePath = server.value.source.firstOrNull()
    val services = composeFilePath?.let { parseComposeServices(resolveYaml(it, uploadedYaml)) } ?: emptyList()
    val serviceNodes = services.mapIndexed { i, name ->
        DiagramNode(
            id = NodeId("${server.key}-service-$i"),
            type = NodeType("composeService"),
            data = NodeData(mapOf("label" to name)),
            parentNode = NodeId(server.key),
            expandParent = true
        )
    }
    return ConfigurationDiagramModel(listOf(groupNode) + serviceNodes, emptyList())
}

private fun parseComposeServices(yaml: String?): List<String> = try {
    if (yaml == null) emptyList()
    else {
        val data = yamlMapper.readValue(yaml, Map::class.java) as Map<*, *>
        (data["services"] as? Map<*, *>)?.keys?.map { it.toString() } ?: emptyList()
    }
} catch (_: Exception) {
    emptyList()
}

private fun resolveYaml(path: String, uploaded: Map<String, String>): String? =
    uploaded[extractFileName(path)] ?: runCatching { java.io.File(path).readText() }.getOrNull()


// ── External JAR ────────────────────────────────────────────────────────────

private fun createExternalJarNode(
    server: Map.Entry<String, LightConfigServer>,
    resolve: Resolve
): ConfigurationDiagramModel {
    val resolved = server.value.externalJarOptions?.options.orEmpty().map(resolve)
    val usesProxy = resolved.any { "-Dhttp.proxyHost=127.0.0.1" in it || "-Dhttp.proxyHost=localhost" in it }
            && resolved.any { "-Dhttp.proxyPort=" in it }
    return serverNodeWithOptionalProxyEdge(server, "externalJar", usesProxy)
}


// ── External URL ────────────────────────────────────────────────────────────

private fun createExternalUrlNode(server: Map.Entry<String, LightConfigServer>): ConfigurationDiagramModel {
    val url = server.value.source.firstOrNull()
    val host = url?.let(::extractHost)
    return DiagramNode(
        id = NodeId(server.key),
        type = NodeType("externalUrl"),
        data = NodeData(buildMap {
            put("label", server.key)
            url?.let { put("url", it) }
            host?.let { put("hostname", it) }
            put("additionalFields", server.value.additionalFields)
        })
    ).singletonModel()
}


// ── Zion ────────────────────────────────────────────────────────────────────

private fun createZionNode(server: Map.Entry<String, LightConfigServer>) =
    serverNodeWithOptionalProxyEdge(server, "zion", usesLocalProxy = true)

internal fun createZionBackendEdges(
    config: LightTigerConfigModel,
    existingModel: ConfigurationDiagramModel,
    resolve: Resolve
): ConfigurationDiagramModel {
    val nodes = mutableListOf<DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()
    val syntheticByUrl = mutableMapOf<String, DiagramNode>()

    config.servers.filter { it.value.type == "zion" }.forEach { (name, server) ->
        server.zionConfiguration?.collectBackendRequestUrls().orEmpty().forEach { url ->
            val resolved = resolve(url)
            val target = existingModel.findNodeMatchingUrl(resolved)
                ?: syntheticByUrl.getOrPut(resolved) { createSyntheticExternalNode(resolved) }.id
            edges += DiagramEdge(
                EdgeId("$name-to-${target.value}-backendRequest"),
                NodeId(name),
                target,
                "backend request"
            )
        }
    }
    nodes += syntheticByUrl.values
    return ConfigurationDiagramModel(nodes, edges.distinctBy { it.id.value })
}

private fun createSyntheticExternalNode(url: String): DiagramNode {
    val host = extractHost(url)
    return DiagramNode(
        id = NodeId("external-backend-${sanitizeId(url)}"),
        type = NodeType("externalUrl"),
        data = NodeData(buildMap {
            put("label", url); put("url", url)
            host?.let { put("hostname", it) }
        })
    )
}


// ── Edge creation: traffic endpoints → tiger proxies ────────────────────────

internal fun createTrafficEndpointEdges(
    config: LightTigerConfigModel,
    resolve: Resolve
): ConfigurationDiagramModel {
    val endpoints = config.tigerProxy?.trafficEndpoints.orEmpty()
    if (endpoints.isEmpty()) return ConfigurationDiagramModel.empty()

    // Extract the port from each endpoint URL after resolving placeholders.
    val endpointPorts = endpoints.mapNotNull { endpoint ->
        extractPortFromUrl(resolve(endpoint))?.let { endpoint to it }
    }

    val edges = mutableListOf<DiagramEdge>()
    config.servers
        .filter { it.value.type == "tigerProxy" && it.value.tigerProxyConfiguration?.adminPort != null }
        .forEach { (serverName, server) ->
            val adminPort = server.tigerProxyConfiguration?.adminPort?.let(resolve) ?: return@forEach
            endpointPorts.filter { (_, port) -> port == adminPort }.forEach { _ ->
                edges += DiagramEdge(
                    EdgeId("$LOCAL_PROXY-to-$serverName-trafficEndpoint"),
                    NodeId(LOCAL_PROXY), NodeId(serverName)
                )
            }
        }
    return ConfigurationDiagramModel.fromEdges(edges)
}


// ── Edge creation: routes → target servers (by serverPort match) ────────────

/**
 * Connects route nodes to the servers they target.
 *
 * For every proxy (local + remote tiger proxies) and each of its routes, resolves the `to` URL,
 * extracts the port, and matches it against each server's `serverPort`. If they match, an edge
 * is created from the route node to the server.
 */
internal fun createRouteToServerEdges(
    config: LightTigerConfigModel,
    resolve: Resolve
): ConfigurationDiagramModel {
    // Collect all servers' resolved serverPort → serverName
    val serversByPort = config.servers.mapNotNull { (name, server) ->
        val port = server["serverPort"]?.toString()?.let(resolve)?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        port to name
    }.groupBy({ it.first }, { it.second })

    if (serversByPort.isEmpty()) return ConfigurationDiagramModel.empty()

    val edges = mutableListOf<DiagramEdge>()

    fun matchRoutes(proxyName: String, routes: List<LightRoute>) {
        routes.forEachIndexed { i, route ->
            val routeNodeId = "$proxyName-route-$i"
            route.to.forEach { toUrl ->
                val port = extractPortFromUrl(resolve(toUrl)) ?: return@forEach
                serversByPort[port]?.forEach { serverName ->
                    edges += DiagramEdge(
                        EdgeId("$routeNodeId-to-$serverName"),
                        NodeId(routeNodeId), NodeId(serverName)
                    )
                }
            }
        }
    }

    // Local proxy routes
    matchRoutes(LOCAL_PROXY, config.tigerProxy?.proxyRoutes.orEmpty())

    // Remote tiger proxy routes
    config.servers
        .filter { it.value.type == "tigerProxy" }
        .forEach { (name, server) ->
            matchRoutes(name, server.tigerProxyConfiguration?.proxyRoutes.orEmpty())
        }

    return ConfigurationDiagramModel.fromEdges(edges)
}


// ── Edge creation: routes → docker containers ───────────────────────────────

internal fun createRouteToDockerEdges(
    config: LightTigerConfigModel,
    resolve: Resolve
): ConfigurationDiagramModel {
    val routes = config.tigerProxy?.proxyRoutes.orEmpty()
    if (routes.isEmpty()) return ConfigurationDiagramModel.empty()

    // Collect each docker server's exposed host ports (resolved to strings).
    val dockerPorts = config.servers
        .filter { it.value.type == "docker" }
        .mapNotNull { (name, server) ->
            val ports = (server.dockerOptions?.get("ports") as? List<*>)
                ?.mapNotNull { resolve(it.toString().substringBefore(":").trim()).takeIf { p -> p.isNotBlank() } }
                ?: emptyList()
            ports.takeIf { it.isNotEmpty() }?.let { name to it }
        }

    val edges = mutableListOf<DiagramEdge>()
    routes.forEachIndexed { i, route ->
        route.to.forEach { toUrl ->
            val port = extractPortFromUrl(resolve(toUrl)) ?: return@forEach
            dockerPorts.filter { (_, ports) -> port in ports }.forEach { (docker, _) ->
                edges += DiagramEdge(
                    EdgeId("$LOCAL_PROXY-route-$i-to-$docker"),
                    NodeId("$LOCAL_PROXY-route-$i"), NodeId(docker)
                )
            }
        }
    }
    return ConfigurationDiagramModel.fromEdges(edges)
}


// ── Edge creation: implicit proxy-to-server routes ──────────────────────────

/**
 * Server types like httpbin, externalJar, externalUrl, and zion automatically register
 * a route `http://<hostname>` → `http://localhost:<serverPort>` on the local tiger proxy
 * at runtime (via `addServerToLocalProxyRouteMap`). These routes are never declared in
 * the YAML, so we create implicit edges from the local proxy to those servers.
 */
private val IMPLICIT_ROUTE_SERVER_TYPES = setOf("httpbin", "externalJar", "externalUrl", "zion")

internal fun createImplicitProxyToServerEdges(
    config: LightTigerConfigModel
): ConfigurationDiagramModel {
    if (!config.localProxyActive) return ConfigurationDiagramModel.empty()

    val edges = config.servers
        .filter { it.value.type in IMPLICIT_ROUTE_SERVER_TYPES }
        .map { (name, _) ->
            DiagramEdge(
                EdgeId("$LOCAL_PROXY-implicit-to-$name"),
                NodeId(LOCAL_PROXY), NodeId(name),
                "implicit route"
            )
        }
    return ConfigurationDiagramModel.fromEdges(edges)
}


// ── Shared helpers ──────────────────────────────────────────────────────────

private fun serverNodeWithOptionalProxyEdge(
    server: Map.Entry<String, LightConfigServer>,
    nodeType: String,
    usesLocalProxy: Boolean
): ConfigurationDiagramModel {
    val node = DiagramNode(
        id = NodeId(server.key),
        type = NodeType(nodeType),
        data = NodeData(mapOf("label" to server.key, "additionalFields" to server.value.additionalFields))
    )
    return if (usesLocalProxy) {
        node.singletonModel() + DiagramEdge(
            EdgeId("${server.key}-to-$LOCAL_PROXY"), NodeId(server.key), NodeId(LOCAL_PROXY), "uses proxy"
        ).singletonModel()
    } else {
        node.singletonModel()
    }
}

private fun extractHost(url: String): String? = runCatching { URI.create(url).host }.getOrNull()

private fun sanitizeId(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "target" }

