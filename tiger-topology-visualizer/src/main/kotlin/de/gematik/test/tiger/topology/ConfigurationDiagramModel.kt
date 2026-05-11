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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import java.net.URI

@JvmInline
value class NodeId(val value: String)

@JvmInline
value class EdgeId(val value: String)

sealed class NodeType(
    @get:JsonValue val value: String
) {
    data object TIGER_PROXY : NodeType("tigerProxy")
    data object DOCKER : NodeType("docker")
    data object GROUP : NodeType("group")
    data object COMPOSE_SERVICE : NodeType("composeService")
    data object EXTERNAL_JAR : NodeType("externalJar")
    data object EXTERNAL_URL : NodeType("externalUrl")
    data object HTTP_BIN : NodeType("httpbin")
    data object ZION : NodeType("zion")
    data object ROUTE : NodeType("route")
    data object DIRECT_REVERSE_TARGET : NodeType("directReverseTarget")
    data class OTHER(val originalType: String) : NodeType(originalType)

    companion object {
        private val knownTypes by lazy { listOf(
            TIGER_PROXY, DOCKER, GROUP, COMPOSE_SERVICE, HTTP_BIN,
            EXTERNAL_JAR, EXTERNAL_URL, ZION, ROUTE, DIRECT_REVERSE_TARGET
        )}

        fun fromServerType(type: String): NodeType =
            knownTypes.find { it.value == type } ?: OTHER(type.ifBlank { "unknown" })
    }
}


data class NodeData(
    val label: String,
    val config: Map<*, *> = emptyMap<String, Any>())


data class ConfigurationDiagramModel(
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val warnings: List<String> = emptyList()
){

    operator fun plus(other: ConfigurationDiagramModel): ConfigurationDiagramModel {
        return this.merge(other)
    }

    fun merge(other: ConfigurationDiagramModel): ConfigurationDiagramModel {
        val mergedNodes = this.nodes + other.nodes
        val mergedEdges = this.edges + other.edges
        val mergedWarnings = this.warnings + other.warnings
        return ConfigurationDiagramModel(mergedNodes, mergedEdges, mergedWarnings)
    }


    fun findNodeMatchingUrl(url: String): NodeId? {
        val urlHost = URI.create(url).host

        return this.nodes.find { it.data.config["hostname"].toString() == urlHost || it.id.value == urlHost }?.id
    }

    fun withWarnings(additionalWarnings: List<String>): ConfigurationDiagramModel =
        if (additionalWarnings.isEmpty()) this
        else copy(warnings = warnings + additionalWarnings)

    companion object {
        fun empty(): ConfigurationDiagramModel {
            return ConfigurationDiagramModel(emptyList(), emptyList())
        }

        fun fromNodes(nodes: List<DiagramNode>): ConfigurationDiagramModel {
            return ConfigurationDiagramModel(nodes, emptyList())
        }

        fun fromEdges(edges: List<DiagramEdge>): ConfigurationDiagramModel {
            return ConfigurationDiagramModel(emptyList(), edges)
        }
    }
}

data class DiagramNode(
    val id: NodeId,
    val type: NodeType,
    val data: NodeData,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val parentNode: NodeId? = null,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val expandParent: Boolean? = null
){


    fun singletonModel(): ConfigurationDiagramModel {
        return ConfigurationDiagramModel(listOf(this), emptyList())
    }
}

data class DiagramEdge(
    val id: EdgeId,
    val source: NodeId,
    val target: NodeId,
    val label: String? = null
) {
    fun singletonModel(): ConfigurationDiagramModel {
        return ConfigurationDiagramModel(emptyList(), listOf(this))
    }
}
