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

package de.gematik.test.tiger.topology.util

import java.net.URI


//Same as require, but with custom exception
inline fun requireThat(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw SafeToSendToClientException(lazyMessage())
}

class SafeToSendToClientException(message: String) : RuntimeException(message)

/** Matches a port in a URL — either a numeric port (`:8080`) or a placeholder (`:${something}`). */
private val PORT_IN_URL = Regex(":(\\$\\{[^}]+}|\\d+)")

/** Extracts a port string from a URL. Returns the port (numeric or placeholder), or null. */
fun extractPortFromUrl(url: String): String? =
    PORT_IN_URL.find(url)?.groupValues?.getOrNull(1)

internal fun extractHost(url: String): String? {
    // If port contains unresolved placeholders, replace with dummy port so URI.create() can parse it
    val parseableUrl = url.replace(Regex(":([^/]*\\$\\{[^}]*})"), ":9999")
    return runCatching { URI.create(parseableUrl).host }.getOrNull()
}