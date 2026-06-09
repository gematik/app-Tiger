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
import kotlin.collections.emptyMap
import kotlin.collections.map

// ──────────────────────────────────────────────────────────────────────────────
// Pure functions that build diagram nodes and edges from a LightTigerConfigModel.
//
// Every function takes data in, returns a ConfigurationDiagramModel fragment.
// The optional [resolve] lambda resolves ${…} placeholders before comparisons.
// ──────────────────────────────────────────────────────────────────────────────

/** Convenience typealias for placeholder resolution. Identity (`{ it }`) when no resolver is available. */
internal typealias ResolvePlaceholders = (String) -> String

private const val DOCKER_HOST = "docker-host"
internal const val LOCAL_PROXY = "localTigerProxy"

internal fun createDiagramNode(
    server: Map.Entry<String, LightConfigServer>,
    yamlResolver: YamlResolver,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel = when (server.value.type) {
    "tigerProxy" -> createTigerProxyNode(server)
    "docker" -> createDockerNode(server)
    "compose" -> createComposeNode(server, yamlResolver)
    "externalJar" -> createExternalJarNode(server, resolvePlaceholders)
    "externalUrl" -> createExternalUrlNode(server)
    "zion" -> createZionNode(server)
    else -> DiagramNode(
        id = NodeId(server.key),
        type = NodeType.fromServerType(server.value.type),
        data = NodeData(server.key, server.value.properties)
    ).singletonModel()
}


internal fun createLocalProxyNode(config: LightTigerConfigModel) = DiagramNode(
    id = NodeId(LOCAL_PROXY),
    type = NodeType.TIGER_PROXY,
    data = NodeData(
        "Local Tiger Proxy",
        config.tigerProxy?.properties ?: emptyMap<String, Any>()
    )
).singletonModel()

private fun createTigerProxyNode(server: Map.Entry<String, LightConfigServer>): ConfigurationDiagramModel {
    val node = DiagramNode(
        id = NodeId(server.key),
        type = NodeType.TIGER_PROXY,
        data = NodeData(
            server.key,
            server.value.properties
        )
    )
    return node.singletonModel() +
            createRouteNodesAndEdges(server.key, server.value.tigerProxyConfiguration?.proxyRoutes.orEmpty())
}

private val LOCALHOST_HOSTS = setOf("localhost", "127.0.0.1")

/** Hostnames that are considered to point at the docker host (i.e. the machine running containers). */
private val DOCKER_HOST_NAMES: Set<String> = LOCALHOST_HOSTS + setOf(
    "host.docker.internal",
    "\${tiger.docker.host}"
)

private fun isLocalhost(url: String): Boolean {
    return LOCALHOST_HOSTS.contains(extractHost(url))
}

private fun resolvedDockerHostNames(resolvePlaceholders: ResolvePlaceholders): Set<String> =
    DOCKER_HOST_NAMES.map(resolvePlaceholders).toSet()


private fun urlTargetsDockerHost(url: String, resolvePlaceholders: ResolvePlaceholders): Boolean {
    val host = extractHost(url) ?: return false
    return host in resolvedDockerHostNames(resolvePlaceholders)
}


/**
 * Creates edges (and possibly synthetic nodes) for each tiger proxy's directReverseProxy.
 *
 * If the target hostname is localhost/127.0.0.1 and a server with a matching `serverPort` exists,
 * the edge points directly to that server. Otherwise, a synthetic directReverseTarget node is created.
 */
internal fun createDirectReverseProxyEdges(
    config: LightTigerConfigModel,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    val serversByPort = collectServersByPort(config, resolvePlaceholders)

    val nodes = mutableListOf<DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()

    config.servers
        .filter { it.value.type == "tigerProxy" }
        .forEach { (name, server) ->
            val reverse = server.tigerProxyConfiguration?.directReverseProxy ?: return@forEach
            val host = reverse.hostname?.takeIf { it.isNotBlank() } ?: return@forEach
            val port = reverse.port?.takeIf { it.isNotBlank() } ?: return@forEach
            val resolvedPort = resolvePlaceholders(port)

            val matchingServer = if (host in LOCALHOST_HOSTS) serversByPort[resolvedPort]?.firstOrNull() else null

            if (matchingServer != null) {
                edges += DiagramEdge.directReverseRoute(NodeId(name), NodeId(matchingServer))
            } else {
                val nodeId = NodeId("$name-directReverseTarget")
                nodes += DiagramNode(
                    id = nodeId,
                    type = NodeType.DIRECT_REVERSE_TARGET,
                    data = NodeData("$host:$port", mapOf("hostname" to host, "port" to port))
                )
                edges += DiagramEdge.directReverseRoute(NodeId(name), nodeId)
            }
        }

    return ConfigurationDiagramModel(nodes, edges)
}

private fun collectServersByPort(
    config: LightTigerConfigModel,
    resolvePlaceholders: ResolvePlaceholders
): Map<String, List<String>> = config.servers.mapNotNull { (name, server) ->
    val port = extractServerPort(server)?.let(resolvePlaceholders)?.takeIf { it.isNotBlank() }
        ?: return@mapNotNull null
    port to name
}.groupBy({ it.first }, { it.second })

private fun collectServersByName(config: LightTigerConfigModel): Map<String, List<String>> =
    config.servers.flatMap { (name, server) ->
        buildList {
            add(name.lowercase())
            server.hostname
                .takeIf { it.isNotBlank() }
                ?.lowercase()
                ?.takeIf { it != name.lowercase() }
                ?.let(::add)
        }.map { normalizedHost -> normalizedHost to name }
    }.groupBy({ it.first }, { it.second })

private fun extractServerPort(server: LightConfigServer): String? {
    return when (server.type) {
        "zion" -> server.zionConfiguration?.serverPort?.takeIf { it.isNotBlank() }
        "httpbin" -> server.properties["serverPort"]?.toString()?.takeIf { it.isNotBlank() }
        else -> null
    }
}


internal fun createRouteNodesAndEdges(proxyName: String, routes: List<LightRoute>): ConfigurationDiagramModel {
    val nodes = mutableListOf<DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()

    routes.forEachIndexed { i, route ->
        val id = NodeId("$proxyName-route-$i")
        nodes += DiagramNode(
            id,
            NodeType.ROUTE,
            NodeData(
                route.from,
                mapOf("from" to route.from, "to" to route.to)
            )
        )
        edges += DiagramEdge.proxyToRoute(proxyName, i, NodeId(proxyName), id)
    }
    return ConfigurationDiagramModel(nodes, edges)
}


internal fun createDockerHostNode() = DiagramNode(
    id = NodeId(DOCKER_HOST),
    type = NodeType.GROUP,
    data = NodeData("docker host")
).singletonModel()

private fun createDockerNode(server: Map.Entry<String, LightConfigServer>): ConfigurationDiagramModel {
    return ConfigurationDiagramModel(
        listOf(
            DiagramNode(
                id = NodeId(server.key),
                type = NodeType.DOCKER,
                data = NodeData(server.key, server.value.properties),
                parentNode = NodeId(DOCKER_HOST),
                expandParent = true
            )
        ),
        listOf(DiagramEdge.implicitRoute(NodeId(LOCAL_PROXY), NodeId(server.key)))
    )
}

private fun createComposeNode(
    server: Map.Entry<String, LightConfigServer>,
    yamlResolver: YamlResolver
): ConfigurationDiagramModel {
    val groupNode = DiagramNode(
        id = NodeId(server.key),
        type = NodeType.GROUP,
        data = NodeData(server.key, server.value.properties),
        parentNode = NodeId(DOCKER_HOST),
        expandParent = true
    )
    val composeFilePath = server.value.source.firstOrNull()
    val resolvedYaml = composeFilePath?.let { yamlResolver.resolve(it) }
    val warnings = if (resolvedYaml == null) {
        listOf("Could not resolve docker-file: $composeFilePath")
    } else {
        emptyList()
    }
    val services = resolvedYaml?.let { parseComposeServices(it) }.orEmpty()

    val serviceNodes = services.entries.mapIndexed { index, entry ->
        ConfigurationDiagramModel(
            listOf(
                DiagramNode(
                    id = NodeId("${server.key}-service-$index"),
                    type = NodeType.COMPOSE_SERVICE,
                    data = NodeData(entry.key, entry.value as Map<*, *>),
                    parentNode = NodeId(server.key),
                    expandParent = true
                )
            ),
            listOf(DiagramEdge.implicitRoute(NodeId(LOCAL_PROXY), NodeId("${server.key}-service-$index")))
        )
    }.fold(ConfigurationDiagramModel.empty()){ acc, model -> acc + model }
    return serviceNodes + ConfigurationDiagramModel(listOf(groupNode), emptyList(), warnings)
}

private fun parseComposeServices(yaml: String): Map<String, Any?> = try {
    val data = yamlMapper.readValue(yaml, Map::class.java) as Map<*, *>
    @Suppress("UNCHECKED_CAST")
    (data["services"] as? Map<String, Any?>) ?: emptyMap()
} catch (_: Exception) {
    emptyMap()
}


private fun createExternalJarNode(
    server: Map.Entry<String, LightConfigServer>,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    val resolved = server.value.externalJarOptions?.options.orEmpty().map(resolvePlaceholders)
    val usesProxy = resolved.any { "-Dhttp.proxyHost=127.0.0.1" in it || "-Dhttp.proxyHost=localhost" in it }
            && resolved.any { "-Dhttp.proxyPort=" in it }
    return serverNodeWithOptionalProxyEdge(server, NodeType.EXTERNAL_JAR, usesProxy)
}


private fun createExternalUrlNode(server: Map.Entry<String, LightConfigServer>): ConfigurationDiagramModel {
    val url = server.value.source.firstOrNull()
    val host = url?.let(::extractHost)
    return DiagramNode(
        id = NodeId(server.key),
        type = NodeType.EXTERNAL_URL,
        data = NodeData(server.key, buildMap {
            host?.let { put("hostname", it) }
            putAll(server.value.properties)
        })
    ).singletonModel()
}


private fun createZionNode(server: Map.Entry<String, LightConfigServer>) =
    serverNodeWithOptionalProxyEdge(server, NodeType.ZION, usesLocalProxy = true)

internal fun createZionBackendEdges(
    config: LightTigerConfigModel,
    existingModel: ConfigurationDiagramModel,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    val nodes = mutableListOf<DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()
    val syntheticByUrl = mutableMapOf<String, DiagramNode>()

    config.servers.filter { it.value.type == "zion" }.forEach { (name, server) ->
        server.zionConfiguration?.collectBackendRequestUrls().orEmpty().forEach { url ->
            val resolved = resolvePlaceholders(url)
            val target = existingModel.findNodeMatchingUrl(resolved)
                ?: syntheticByUrl.getOrPut(resolved) { createSyntheticExternalNode(resolved) }.id
            edges += DiagramEdge.makesBackendRequest(NodeId(name), target, NodeId(LOCAL_PROXY))
        }
    }
    nodes += syntheticByUrl.values
    return ConfigurationDiagramModel(nodes, edges.distinctBy { it.id.value })
}

private fun createSyntheticExternalNode(url: String): DiagramNode {
    val host = extractHost(url)
    return DiagramNode(
        id = NodeId("external-backend-${sanitizeId(url)}"),
        type = NodeType.EXTERNAL_URL,
        data = NodeData(url, buildMap {
            host?.let { put("hostname", it) }
        })
    )
}


internal fun createTrafficEndpointEdges(
    config: LightTigerConfigModel,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    var result = ConfigurationDiagramModel.empty()
    if (config.localProxyActive && !config.tigerProxy?.trafficEndpoints.isNullOrEmpty()) {
        result += createTrafficEndpointEdgesForProxy(
            NodeId(LOCAL_PROXY),
            config.tigerProxy?.trafficEndpoints?.map(resolvePlaceholders).orEmpty(),
            config,
            resolvePlaceholders
        )
    }
    config.servers.filter { it.value.type == "tigerProxy" && !it.value.tigerProxyConfiguration?.trafficEndpoints.isNullOrEmpty() }
        .forEach { (serverName, server) ->
            result += createTrafficEndpointEdgesForProxy(
                NodeId(serverName),
                server.tigerProxyConfiguration?.trafficEndpoints?.map(resolvePlaceholders).orEmpty(),
                config,
                resolvePlaceholders
            )
        }

    return result

}


private fun createTrafficEndpointEdgesForProxy(
    source: NodeId,
    trafficEndpoints: List<String>,
    config: LightTigerConfigModel,
    resolvePlaceholders: ResolvePlaceholders,
): ConfigurationDiagramModel {
    if (trafficEndpoints.isEmpty()) {
        return ConfigurationDiagramModel.empty()
    }

    val tigerProxyByAdminPort = config.servers.entries
        .asSequence()
        //cover the case where a proxy subscribes traffic from the local tiger proxy
        .plus(mapOf(LOCAL_PROXY to LightConfigServer.fromLocalProxy(config)).entries.asSequence())
        .filter { it.value.type == "tigerProxy" }
        .mapNotNull { entry ->
            entry.value.tigerProxyConfiguration?.adminPort
                ?.let { port -> resolvePlaceholders(port).takeIf { it.isNotBlank() } }
                ?.let { it to entry.key }
        }
        .toMap()

    val edges = mutableListOf<DiagramEdge>()
    val nodesById = linkedMapOf<NodeId, DiagramNode>()

    val (localHostTrafficEndpoints, initialExternalEndpoints) = trafficEndpoints.partition(::isLocalhost)
    val externalEndpoints = initialExternalEndpoints.toMutableList()

    localHostTrafficEndpoints.forEach { local ->
        val port = extractPortFromUrl(local)
        val matchingProxyId = port?.let { tigerProxyByAdminPort[it] }
        if (matchingProxyId != null) {
            edges += DiagramEdge.trafficSubscription(source, NodeId(matchingProxyId))
        } else {
            externalEndpoints += local
        }
    }

    externalEndpoints.forEach { external ->
        val nodeId = NodeId(sanitizeId(external))
        nodesById.putIfAbsent(
            nodeId,
            DiagramNode(
                id = nodeId,
                type = NodeType.TIGER_PROXY_EXTERNAL,
                data = NodeData(external)
            )
        )
        edges += DiagramEdge.trafficSubscription(source, nodeId)
    }

    return ConfigurationDiagramModel(
        nodes = nodesById.values.toList(),
        edges = edges.distinctBy { it.id.value }
    )
}

/**
 * Connects route nodes to the servers they target.
 *
 * For every proxy (local + remote tiger proxies) and each of its routes, resolves the `to` URL,
 * extracts the port, and matches it against each server's `serverPort`. If they match, an edge
 * is created from the route node to the server.
 */
internal fun createRouteToServerEdges(
    config: LightTigerConfigModel,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    val serversByPort = collectServersByPort(config, resolvePlaceholders)
    val serversByName = collectServersByName(config)

    if (serversByPort.isEmpty() && serversByName.isEmpty()) return ConfigurationDiagramModel.empty()

    val edges = mutableListOf<DiagramEdge>()

    fun matchRoutes(proxyName: String, routes: List<LightRoute>) {
        routes.forEachIndexed { i, route ->
            val routeNodeId = "$proxyName-route-$i"
            route.to.forEach { toUrl ->
                val resolvedUrl = resolvePlaceholders(toUrl)
                val portMatches = extractPortFromUrl(resolvedUrl)
                    ?.let { serversByPort[it].orEmpty() }
                    .orEmpty()
                val hostMatches = extractHost(resolvedUrl)
                    ?.lowercase()
                    ?.let { serversByName[it].orEmpty() }
                    .orEmpty()

                (portMatches + hostMatches).distinct().forEach { serverName ->
                    edges += DiagramEdge.routeToTarget(NodeId(routeNodeId), NodeId(serverName))
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


internal fun createRouteToDockerEdges(
    config: LightTigerConfigModel,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    val dockerPorts = config.servers
        .filter { it.value.type == "docker" }
        .mapNotNull { (name, server) ->
            val ports = server.dockerOptions?.ports.orEmpty()
                .map { resolvePlaceholders(it.substringBefore(":").trim()) }
                .filter { it.isNotBlank() }
            ports.takeIf { it.isNotEmpty() }?.let { NodeId(name) to it }
        }
    return createRouteToTargetByPortEdges(config, dockerPorts, resolvePlaceholders)
}


/**
 * Connects route nodes to compose service nodes by matching the route's target port
 * against the compose service's exposed host ports.
 *
 * Compose services declare ports in docker-compose format (e.g. "8080:80"), where the
 * left side is the host port. This function extracts those host ports from the already-
 * created compose service nodes in the model and matches them against route target URLs.
 */
internal fun createRouteToComposeServiceEdges(
    config: LightTigerConfigModel,
    existingModel: ConfigurationDiagramModel,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    val composeServicePorts = existingModel.nodes
        .filter { it.type == NodeType.COMPOSE_SERVICE }
        .mapNotNull { node ->
            val ports = extractHostPortsFromComposeService(node.data.config)
            ports.takeIf { it.isNotEmpty() }?.let { node.id to it }
        }
    return createRouteToTargetByPortEdges(config, composeServicePorts, resolvePlaceholders)
}

/**
 * Connect route nodes to docker/compose targets by matching
 * the route's target port against each target's exposed host ports.
 *
 * Only routes whose `to` URL points at a recognized docker host are considered.
 */
private fun createRouteToTargetByPortEdges(
    config: LightTigerConfigModel,
    targetPorts: List<Pair<NodeId, List<String>>>,
    resolvePlaceholders: ResolvePlaceholders
): ConfigurationDiagramModel {
    if (targetPorts.isEmpty()) return ConfigurationDiagramModel.empty()

    val edges = mutableListOf<DiagramEdge>()

    fun matchRoutes(proxyName: String, routes: List<LightRoute>) {
        routes.forEachIndexed { i, route ->
            val routeNodeId = "$proxyName-route-$i"
            route.to.forEach { toUrl ->
                val resolvedUrl = resolvePlaceholders(toUrl)
                if (!urlTargetsDockerHost(resolvedUrl, resolvePlaceholders)) return@forEach
                val port = extractPortFromUrl(resolvedUrl) ?: return@forEach
                targetPorts.filter { (_, ports) -> port in ports }.forEach { (targetId, _) ->
                    edges += DiagramEdge.routeToTarget(NodeId(routeNodeId), targetId)
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

/**
 * Extracts host ports from a compose service's config map.
 * Ports in docker-compose format: "hostPort:containerPort" or just "port".
 */
private fun extractHostPortsFromComposeService(config: Map<*, *>): List<String> {
    val ports = config["ports"] as? List<*> ?: return emptyList()
    return ports.mapNotNull { portMapping ->
        val portStr = portMapping.toString().trim()
        portStr.substringBefore(":").trim().takeIf { it.isNotBlank() }
    }
}


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

    return config.servers
        .filter { it.value.type in IMPLICIT_ROUTE_SERVER_TYPES }
        .map { (serverId, _) ->
            DiagramEdge.implicitRoute(NodeId(LOCAL_PROXY), NodeId(serverId))
        }.let { ConfigurationDiagramModel.fromEdges(it) }
}


private fun serverNodeWithOptionalProxyEdge(
    server: Map.Entry<String, LightConfigServer>,
    nodeType: NodeType,
    usesLocalProxy: Boolean
): ConfigurationDiagramModel {
    val node = DiagramNode(
        id = NodeId(server.key),
        type = nodeType,
        data = NodeData(server.key, server.value.properties)
    )
    return if (usesLocalProxy) {
        node.singletonModel() + DiagramEdge.usesProxy(NodeId(server.key), NodeId(LOCAL_PROXY)).singletonModel()
    } else {
        node.singletonModel()
    }
}

private fun extractHost(url: String): String? {
    // If port contains unresolved placeholders, replace with dummy port so URI.create() can parse it
    val parseableUrl = url.replace(Regex(":([^/]*\\$\\{[^}]*})"), ":9999")
    return runCatching { URI.create(parseableUrl).host }.getOrNull()
}

private fun sanitizeId(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "target" }


/** Edge types that should be merged into a single bidirectional edge when they connect the same two nodes. */
private val MERGEABLE_EDGE_TYPES = setOf(EdgeType.IMPLICIT_ROUTE, EdgeType.USES_PROXY)

/**
 * Merges pairs of edges between the same two nodes (in opposite directions) into a single
 * bidirectional edge with combined label and arrows on both ends.
 *
 * Only edge types in [MERGEABLE_EDGE_TYPES] are candidates for merging.
 * Non-mergeable edges are passed through unchanged.
 */
internal fun mergeBidirectionalEdges(model: ConfigurationDiagramModel): ConfigurationDiagramModel {
    val (mergeable, other) = model.edges.partition { it.type in MERGEABLE_EDGE_TYPES }

    fun pairKey(edge: DiagramEdge): Set<String> = setOf(edge.source.value, edge.target.value)

    val byPair = mergeable.groupBy { pairKey(it) }

    val merged = byPair.map { (_, edges) ->
        when (edges.size) {
            1 -> edges.single()
            else -> {
                val combinedLabel = edges.mapNotNull { it.label }.distinct().joinToString(" / ").ifBlank { null }
                edges.first().copy(
                    id = EdgeId(edges.joinToString("-and-") { it.id.value }),
                    type = EdgeType.PROXY_AND_ROUTE,
                    label = combinedLabel,
                    markerEnd = "arrowclosed",
                    markerStart = "arrowclosed"
                )
            }
        }
    }

    return model.copy(edges = other + merged)
}


