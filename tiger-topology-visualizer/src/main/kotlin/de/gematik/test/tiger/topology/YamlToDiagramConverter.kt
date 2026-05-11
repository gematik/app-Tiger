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

import de.gematik.test.tiger.topology.deserialization.LightTigerConfigModel
import de.gematik.test.tiger.topology.deserialization.resolvePlaceholders
import de.gematik.test.tiger.topology.util.requireThat

// ──────────────────────────────────────────────────────────────────────────────
// Entry points for converting Tiger YAML configurations into diagram models.
//
// The heavy lifting is delegated to:
//   - YamlMerger.kt  → YAML parsing, normalisation, multi-file merge
//   - NodeFactory.kt → diagram node & edge creation
// ──────────────────────────────────────────────────────────────────────────────

data class UploadedYamlFile(val fileName: String, val content: String)

/**
 * Main entry point: takes a list of uploaded YAML files, merges them, resolves placeholders, and builds the full diagram model.
 */
fun convertUploadedFilesToDiagramModel(uploadedFiles: List<UploadedYamlFile>): ConfigurationDiagramModel {
    if (uploadedFiles.isEmpty()) return ConfigurationDiagramModel.empty()

    val byName = uploadedFiles.groupBy { extractFileName(it.fileName) }
    val duplicates = byName.filterValues { it.size > 1 }.keys
    requireThat(duplicates.isEmpty()) {
        "Uploaded YAML files must have unique file names. Duplicates: ${duplicates.sorted().joinToString(", ")}"
    }

    val uploadedYaml = byName.mapValues { it.value.single().content }
    val globalConfig = mergeGlobalConfig(uploadedFiles)

    return convertConfigurationToDiagramModel(
        config = globalConfig.tigerConfig,
        yamlResolver = UploadedYamlResolver(uploadedYaml),
        resolvePlaceholders = { resolvePlaceholders(it, globalConfig) }
    ).withWarnings(globalConfig.warnings)
}

/**
 * Builds a [ConfigurationDiagramModel] from a [LightTigerConfigModel].
 *
 * @param resolvePlaceholders resolves `${…}` placeholders before port/URL comparisons. Defaults to identity.
 */
fun convertConfigurationToDiagramModel(
    config: LightTigerConfigModel,
    yamlResolver: YamlResolver,
    resolvePlaceholders: ResolvePlaceholders = { it }
): ConfigurationDiagramModel {
    var model = ConfigurationDiagramModel.empty()

    // 1. Local tiger proxy + its routes
    if (config.localProxyActive) {
        model += createLocalProxyNode(config)
        model += createRouteNodesAndEdges("localTigerProxy", config.tigerProxy?.proxyRoutes.orEmpty())
    }

    // 2. Docker host group (if any docker/compose servers exist)
    if (config.servers.any { it.value.type == "docker" || it.value.type == "compose" }) {
        model += createDockerHostNode()
    }

    // 3. All server nodes
    config.servers.forEach { server ->
        model += createDiagramNode(server, yamlResolver, resolvePlaceholders)
    }

    // 4. Edges from local proxy to remote proxies (traffic endpoints) and docker containers (routes)
    if (config.localProxyActive) {
        model += createTrafficEndpointEdges(config, resolvePlaceholders)
        model += createRouteToDockerEdges(config, resolvePlaceholders)
    }

    // 5. Edges from route nodes to the servers they target (by serverPort match)
    model += createRouteToServerEdges(config, resolvePlaceholders)

    // 6. Implicit edges from local proxy to servers that auto-register routes at runtime
    //TODO: not sure if useful
    //model += createImplicitProxyToServerEdges(config)

    // 7. Zion backend request edges
    model += createZionBackendEdges(config, model, resolvePlaceholders)

    return model
}
