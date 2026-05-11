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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.gematik.test.tiger.topology.deserialization.GlobalConfig
import de.gematik.test.tiger.topology.deserialization.LightAdditionalConfigurationFile
import de.gematik.test.tiger.topology.deserialization.LightTigerConfigModel
import de.gematik.test.tiger.topology.util.requireThat

// ──────────────────────────────────────────────────────────────────────────────
// YAML parsing, normalisation, and multi-file merge logic.
//
// Entry point: [mergeGlobalConfig] takes a list of uploaded YAML files and
// produces a [GlobalConfig] containing the merged [LightTigerConfigModel]
// (the "tiger" section) plus the full configuration tree for placeholder
// resolution.
// ──────────────────────────────────────────────────────────────────────────────

private const val TIGER_KEY = "tiger"
private val TIGER_ROOT_KEYS = setOf("servers", "tigerProxy", "localProxyActive", "additionalConfigurationFiles")

internal val yamlMapper: ObjectMapper =
    ObjectMapper(YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerKotlinModule()


// ── Public entry point ──────────────────────────────────────────────────────

/**
 * Merges uploaded YAML files into a single [GlobalConfig].
 *
 * Exactly one file must be the **root** tiger configuration (containing keys like `servers`,
 * `tigerProxy`, etc. or a `tiger:` root key). All other tiger configuration files must be
 * referenced via `additionalConfigurationFiles` from the root. Non-tiger files (e.g.
 * docker-compose files) are kept available for reference resolution but don't count as roots.
 */
internal fun mergeGlobalConfig(uploadedFiles: List<UploadedYamlFile>): GlobalConfig {
    val yamlByFileName = uploadedFiles.associate { extractFileName(it.fileName) to it.content }
    val additionalFileNames = collectAdditionalFileNames(uploadedFiles)

    val rootFiles = uploadedFiles.filter { file ->
        extractFileName(file.fileName) !in additionalFileNames && isTigerConfig(parseYamlDocument(file.content))
    }

    requireThat(rootFiles.size <= 1) {
        "Expected exactly one root tiger configuration file, but found ${rootFiles.size}: " +
                rootFiles.map { extractFileName(it.fileName) }.sorted().joinToString(", ")
    }

    val rootFile = rootFiles.singleOrNull()
        ?: return GlobalConfig(LightTigerConfigModel())

    val (tree, warnings) = buildRootedTree(rootFile, yamlByFileName)

    val tigerSection = tree[TIGER_KEY] as? Map<*, *> ?: emptyMap<String, Any?>()
    val tigerConfig = yamlMapper.convertValue(normalizeMap(tigerSection), LightTigerConfigModel::class.java)
    return GlobalConfig(tigerConfig, tree, warnings)
}


// ── YAML parsing & normalisation ────────────────────────────────────────────

/**
 * Parses raw YAML text into a normalised `Map<String, Any?>`.
 * Normalisation ensures all map keys are [String] (Jackson may produce Integer/Boolean keys).
 */
internal fun parseYamlDocument(yamlContent: String): Map<String, Any?> {
    return when (val parsed = yamlMapper.readValue(yamlContent, Any::class.java)) {
        is Map<*, *> -> normalizeMap(parsed)
        else -> emptyMap()
    }
}

/**
 * Recursively converts a `Map<*, *>` into a `LinkedHashMap<String, Any?>`, ensuring
 * all keys are strings and all nested maps/lists are normalised. Null keys are dropped.
 */
internal fun normalizeMap(map: Map<*, *>): LinkedHashMap<String, Any?> {
    return linkedMapOf<String, Any?>().apply {
        map.forEach { (key, value) ->
            if (key != null) put(key.toString(), normalizeValue(value))
        }
    }
}

private fun normalizeValue(value: Any?): Any? = when (value) {
    is Map<*, *> -> normalizeMap(value)
    is List<*> -> value.map(::normalizeValue)
    else -> value
}


// ── Tree construction ───────────────────────────────────────────────────────

/** Collects filenames that are referenced as additionalConfigurationFiles in any uploaded file. */
private fun collectAdditionalFileNames(files: List<UploadedYamlFile>): Set<String> =
    files.flatMap { readAdditionalFiles(parseYamlDocument(it.content)).map { acf -> extractFileName(acf.filename) } }
        .toSet()

/** Result of [buildRootedTree]: the merged tree plus any warnings collected during resolution. */
private data class TreeBuildResult(
    val tree: LinkedHashMap<String, Any?>,
    val warnings: List<String>) {

    companion object {
        fun emptyResult() : TreeBuildResult =
             TreeBuildResult(linkedMapOf(), emptyList())

        fun justWarnings(warnings: List<String>) : TreeBuildResult =
            TreeBuildResult(linkedMapOf(), warnings)

    }

}

/**
 * Builds a rooted configuration tree for a single uploaded file, including any
 * additionalConfigurationFiles it references (resolved transitively).
 */
private fun buildRootedTree(
    file: UploadedYamlFile,
    yamlByFileName: Map<String, String>
): TreeBuildResult {
    val document = parseYamlDocument(file.content)
    val tree = rootAsTigerConfig(document)
    val processed = mutableSetOf<String>()
    val warnings = mutableListOf<String>()

    while (true) {
        val pending = readAdditionalFiles(tree)
            .filter { it.filename.isNotBlank() }
            .filter { processed.add(extractFileName(it.filename)) }
        if (pending.isEmpty()) return TreeBuildResult(tree, warnings)

        pending.forEach { ref ->
            val yaml = resolveYamlContent(ref.filename, yamlByFileName)
            if (yaml == null) {
                warnings += "Additional configuration file not found: ${ref.filename}"
                return@forEach
            }
            val additional = buildAdditionalTree(yaml, ref.baseKey)
            ensureNoDuplicateServers(tree[TIGER_KEY] as? Map<*, *>, additional[TIGER_KEY] as? Map<*, *>)
            deepMerge(tree, additional)
        }
    }
}

/** Wraps a parsed document under the "tiger" key if it isn't already. */
private fun rootAsTigerConfig(document: Map<String, Any?>): LinkedHashMap<String, Any?> = when {
    document[TIGER_KEY] is Map<*, *> -> normalizeMap(document)
    document.keys.any { it in TIGER_ROOT_KEYS } -> linkedMapOf(TIGER_KEY to normalizeMap(document))
    else -> error("The root configuration file must contain a 'tiger' section or at least one of the following keys: ${TIGER_ROOT_KEYS.joinToString(", ")}")
}

/** Returns true if the document looks like a tiger configuration. */
private fun isTigerConfig(document: Map<String, Any?>): Boolean =
    document[TIGER_KEY] is Map<*, *> || document.keys.any { it in TIGER_ROOT_KEYS }

/** Builds a tree from an additional config file, optionally nesting it under [baseKey]. */
private fun buildAdditionalTree(yamlContent: String, baseKey: String?): LinkedHashMap<String, Any?> {
    val document = parseYamlDocument(yamlContent)
    return if (baseKey.isNullOrBlank()) {
        normalizeMap(document)
    } else {
        linkedMapOf<String, Any?>().apply { mergeAtPath(this, baseKey, document) }
    }
}

/** Reads the `additionalConfigurationFiles` list from the tiger section of a config tree. */
private fun readAdditionalFiles(tree: Map<String, Any?>): List<LightAdditionalConfigurationFile> {
    val tigerSection = when {
        tree[TIGER_KEY] is Map<*, *> -> normalizeMap(tree[TIGER_KEY] as Map<*, *>)
        tree.keys.any { it in TIGER_ROOT_KEYS } -> normalizeMap(tree)
        else -> return emptyList()
    }
    return yamlMapper.convertValue(tigerSection, LightTigerConfigModel::class.java).additionalConfigurationFiles
}


// ── Deep merge helpers ──────────────────────────────────────────────────────

/** Deeply merges [additional] into [base]. Maps are merged recursively, lists are concatenated. */
internal fun deepMerge(base: MutableMap<String, Any?>, additional: Map<String, Any?>) {
    additional.forEach { (key, value) ->
        when (val existing = base[key]) {
            is MutableMap<*, *> if value is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                deepMerge(existing as MutableMap<String, Any?>, normalizeMap(value))
            }
            is Map<*, *> if value is Map<*, *> -> {
                val merged = normalizeMap(existing)
                deepMerge(merged, normalizeMap(value))
                base[key] = merged
            }
            is List<*> if value is List<*> -> base[key] = existing + value
            else -> base[key] = value
        }
    }
}

/** Inserts [document] into [root] at the dot-separated [path] (e.g. "tiger.servers"). */
private fun mergeAtPath(root: MutableMap<String, Any?>, path: String, document: Map<String, Any?>) {
    val segments = path.split('.').filter { it.isNotBlank() }
    if (segments.isEmpty()) {
        deepMerge(root, normalizeMap(document))
        return
    }
    var current = root
    segments.dropLast(1).forEach { segment ->
        current = when (val existing = current[segment]) {
            is MutableMap<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                existing as MutableMap<String, Any?>
            }
            is Map<*, *> -> normalizeMap(existing).also { current[segment] = it }
            else -> linkedMapOf<String, Any?>().also { current[segment] = it }
        }
    }
    deepMerge(current, linkedMapOf(segments.last() to normalizeMap(document)))
}


// ── Validation ──────────────────────────────────────────────────────────────

private fun ensureNoDuplicateServers(existing: Map<*, *>?, incoming: Map<*, *>?) {
    val existingNames = (existing?.get("servers") as? Map<*, *>)?.keys?.map { it.toString() }?.toSet() ?: emptySet()
    val incomingNames = (incoming?.get("servers") as? Map<*, *>)?.keys?.map { it.toString() }?.toSet() ?: emptySet()
    val duplicates = existingNames intersect incomingNames
    requireThat(duplicates.isEmpty()) {
        "Server definitions must be unique across uploaded files. Duplicates: ${duplicates.sorted().joinToString(", ")}"
    }
}


// ── File resolution ─────────────────────────────────────────────────────────

private fun resolveYamlContent(referencePath: String, yamlByFileName: Map<String, String>): String? =
    yamlByFileName[extractFileName(referencePath)]

internal fun extractFileName(path: String): String =
    path.substringAfterLast('/').substringAfterLast('\\')

