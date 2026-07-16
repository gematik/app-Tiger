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

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule

// Classes store a Map<String, Any?> internally via @JsonAnySetter, but provide typed getters
// for the fields we need when building the topology visualizer.
// The reason for keeping the Map as a backing structure is for
// easier resolving of placeholders. e.g. ${tiger.servers.something} can be directly retrieved.

class LightTigerConfigModel : SerializableMap() {
    val tigerProxy: LightTigerProxyConfiguration? get() = typed("tigerProxy")
    val localProxyActive: Boolean get() = properties["localProxyActive"]?.toString()?.toBoolean() ?: true
    val additionalConfigurationFiles: List<LightAdditionalConfigurationFile>
        get() = typedList("additionalConfigurationFiles")
    val servers: Map<String, LightConfigServer> get() = typedMap("servers")
}

/**
 * Wraps the [tigerConfig] together with the full configuration tree (including any non-tiger
 * root keys, e.g. from additionalConfigurationFiles without baseKey). Used for placeholder
 * resolution where paths like `${tiger.servers.x}` and `${config_ports.y}` must both work.
 */
class GlobalConfig(
    val tigerConfig: LightTigerConfigModel = LightTigerConfigModel(),
    private val fullTree: Map<String, Any?> = emptyMap(),
    val warnings: List<String> = listOf()
) {
    /** Resolves a dot-separated path against the full configuration tree. */
    operator fun get(path: String): Any? = resolvePathInMap(path, fullTree)
}

class LightAdditionalConfigurationFile(
    val filename: String = "",
    val baseKey: String? = null
)

class LightRoute(
    val from: String,
    @field:JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val to: List<String>
)

class LightConfigServer : SerializableMap() {
    val hostname: String get() = properties["hostname"] as? String ?: ""
    val type: String get() = properties["type"] as? String ?: ""
    val source: List<String>
        get() = (properties["source"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

    val tigerProxyConfiguration: LightTigerProxyConfiguration? get() = typed("tigerProxyConfiguration")
    val externalJarOptions: LightExternalJarOptions? get() = typed("externalJarOptions")
    val dockerOptions: LightDockerOptions? get() = typed("dockerOptions")
    val zionConfiguration: LightZionConfiguration? get() = typed("zionConfiguration")

    companion object {
        fun fromLocalProxy(localProxyConfig: LightTigerConfigModel): LightConfigServer {
            return LightConfigServer().apply {
                properties["type"] = "tigerProxy"
                properties["tigerProxyConfiguration"] = localProxyConfig.tigerProxy ?: LightTigerProxyConfiguration()
            }
        }
    }
}

class LightZionConfiguration : SerializableMap() {
    val mockResponses: Map<String, LightMockResponse> get() = typedMap("mockResponses")
    val serverPort: String? get() = properties["serverPort"]?.toString()

    fun collectBackendRequestUrls(): List<String> {
        fun collect(response: LightMockResponse): List<String> {
            val fromThis =
                response.backendRequests.values.mapNotNull { backendRequest -> backendRequest.url.takeIf { it.isNotBlank() } }
            val fromNested = response.nestedResponses.values.flatMap { collect(it) }
            return fromThis + fromNested
        }
        return mockResponses.values.flatMap { collect(it) }.distinct()
    }
}

class LightMockResponse : SerializableMap() {
    val nestedResponses: Map<String, LightMockResponse> get() = typedMap("nestedResponses")
    val backendRequests: Map<String, LightBackendRequest> get() = typedMap("backendRequests")
}

class LightBackendRequest : SerializableMap() {
    val url: String get() = properties["url"] as? String ?: ""
}

class LightDockerOptions : SerializableMap() {
    val ports: List<String>
        get() = (properties["ports"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
}

class LightExternalJarOptions : SerializableMap() {
    val options: List<String>
        get() = (properties["options"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
}

class LightTigerProxyConfiguration : SerializableMap() {
    val adminPort: String? get() = properties["adminPort"]?.toString()
    val proxyPort: String? get() = properties["proxyPort"]?.toString()
    val directReverseProxy: LightDirectReverseProxyInfo? get() = typed("directReverseProxy")
    val proxyRoutes: List<LightRoute> get() = typedList("proxyRoutes")
    val trafficEndpoints: List<String>
        get() = (properties["trafficEndpoints"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

}

class LightDirectReverseProxyInfo : SerializableMap() {
    val hostname: String? get() = properties["hostname"] as? String
    val port: String? get() = properties["port"]?.toString()
}

private val PLACEHOLDER_PATTERN = Regex("\\$\\{([^}]+)}")

/**
 * Resolves `${path.to.value}` placeholders in [input] by looking up dot-separated paths
 * in the full configuration tree of [config]. Paths are resolved against the complete tree
 * which includes the "tiger" root key and any non-tiger root keys (e.g. from additional
 * configuration files without baseKey).
 *
 * Resolves transitively: if a resolved value itself contains placeholders, they are resolved
 * too. Stops when no more replacements are possible or after [maxDepth] iterations (to guard
 * against circular references). Unresolvable placeholders are left as-is.
 */
fun resolvePlaceholders(
    input: String,
    config: GlobalConfig,
    maxDepth: Int = 50
): String {
    var result = input
    repeat(maxDepth) {
        val previous = result
        result = PLACEHOLDER_PATTERN.replace(result) { match ->
            config[match.groupValues[1]]?.toString() ?: match.value
        }
        if (result == previous) return result
    }
    return result
}

internal fun resolvePathInMap(path: String, map: Map<*, *>): Any? {
    val segments = path.split('.')
    var current: Any? = map
    for (segment in segments) {
        current = (current as? Map<*, *>)?.get(segment) ?: return null
    }
    return current
}


@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.NONE
)
open class SerializableMap {
    // Single source of truth for ALL fields
    @get:JsonValue
    val properties: MutableMap<String, Any> = mutableMapOf()

    /** Supports dot-separated path traversal, e.g. "servers.serverA.port" */
    operator fun get(path: String): Any? {
        val segments = path.split('.')
        var current: Any? = properties
        for (segment in segments) {
            current = (current as? Map<*, *>)?.get(segment) ?: return null
        }
        return current
    }

    @JsonAnySetter
    fun set(name: String, value: Any?) {
        if (value != null) {
            properties[name] = value
        }
    }

    /** Typed access helper — lazily converts nested Maps to typed objects */
    protected inline fun <reified T> typed(key: String): T? {
        return when (val raw = properties[key]) {
            is T -> raw
            is Map<*, *> -> SHARED_MAPPER
                .convertValue(raw, T::class.java)
                .also { properties[key] = it as Any }

            else -> null
        }
    }

    /** Typed list access — lazily converts a raw list of Maps to List<T> */
    protected inline fun <reified T> typedList(key: String): List<T> {
        val raw = properties[key] ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        if (raw is List<*> && (raw.isEmpty() || raw.firstOrNull() is T)) return raw as List<T>
        val javaType = SHARED_MAPPER.typeFactory.constructCollectionType(List::class.java, T::class.java)
        return SHARED_MAPPER.convertValue<List<T>>(raw, javaType).also { properties[key] = it }
    }

    /** Typed map access — lazily converts a raw Map<String, Map> to Map<String, V> */
    protected inline fun <reified V> typedMap(key: String): Map<String, V> {
        val raw = properties[key] ?: return emptyMap()
        @Suppress("UNCHECKED_CAST")
        if (raw is Map<*, *> && (raw.isEmpty() || raw.values.firstOrNull() is V)) return raw as Map<String, V>
        val javaType = SHARED_MAPPER.typeFactory.constructMapType(Map::class.java, String::class.java, V::class.java)
        return SHARED_MAPPER.convertValue<Map<String, V>>(raw, javaType).also { properties[key] = it }
    }

    companion object {
        val SHARED_MAPPER: ObjectMapper =
            YAMLMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(kotlinModule())
                .build()
    }
}