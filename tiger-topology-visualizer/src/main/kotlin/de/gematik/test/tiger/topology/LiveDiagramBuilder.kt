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

import de.gematik.test.tiger.topology.deserialization.LightTigerProxyConfiguration

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration
import de.gematik.test.tiger.topology.deserialization.LightTigerConfigModel
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}
// ──────────────────────────────────────────────────────────────────────────────
// Builds a topology diagram from the live TigerGlobalConfiguration.
//
// This reads the already-initialised configuration — it will NOT trigger
// initialisation. If TigerGlobalConfiguration has not been initialised yet,
// an empty diagram is returned.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Reads the current tiger configuration from [TigerGlobalConfiguration] and
 * builds a [ConfigurationDiagramModel].
 *
 * Safe to call at any time:
 * - If the configuration is already initialised → returns the full diagram.
 * - If not yet initialised → returns [ConfigurationDiagramModel.empty] without side effects.
 */
fun buildDiagramFromLiveConfiguration(): ConfigurationDiagramModel {
    if (!TigerGlobalConfiguration.isInitialized()) {
        return ConfigurationDiagramModel.empty()
    }

    val tigerConfig = TigerGlobalConfiguration.instantiateConfigurationBean(
        LightTigerConfigModel::class.java, "tiger"
    ).orElse(LightTigerConfigModel())

    return convertConfigurationToDiagramModel(
        config = tigerConfig ?: LightTigerConfigModel(),
        yamlResolver = FilesystemYamlResolver(),
        resolvePlaceholders = TigerGlobalConfiguration::resolvePlaceholders
    )
}

/**
 * Variant that enriches external tiger proxy nodes by attempting to read their live
 * configuration from the remote proxy admin endpoint. If the remote config cannot be
 * read (null or exception), the node is left unchanged.
 */
fun buildDiagramFromLiveConfiguration(
    reader: RemoteProxyConfigurationReader
): ConfigurationDiagramModel {
    val base = buildDiagramFromLiveConfiguration()

    val updatedNodes = base.nodes.map { node ->
        if (node.type != NodeType.TIGER_PROXY_EXTERNAL) return@map node

        //We store the url from trafficSubscription in the label
        val adminUrl = node.data.label

        val uri = try {
            java.net.URI.create(adminUrl)
        } catch (e: Exception) {
            logger.warn{"Failed to parse admin url for node '${node.id.value}': ${e.message?.take(200)}"}
            null
        }

        if (uri == null) return@map node

        val fetched: LightTigerProxyConfiguration? = try {
            reader.readTigerProxyConfig(uri)
        } catch (e: Exception) {
            logger.warn{"Failed to read remote config for node '${node.id.value}': ${e.message?.take(200)}"}
            null
        }

        if (fetched == null) return@map node

        // Replace node.data.config with the fetched config backing map
        val newData = node.data.copy(config = fetched.properties)
        node.copy(data = newData)
    }

    return ConfigurationDiagramModel(updatedNodes, base.edges)
}



