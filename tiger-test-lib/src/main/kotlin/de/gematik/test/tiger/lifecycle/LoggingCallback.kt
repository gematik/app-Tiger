/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.lifecycle

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class LoggingCallback : LifecycleCallbacks {
    override fun beforeReadConfiguration() {
        logger.trace { "Before read configuration" }
    }

    override fun afterReadConfiguration() {
        logger.trace { "After read configuration" }
    }

    override fun beforeServersStart() {
        logger.trace { "Before starting servers" }
    }

    override fun afterServersStart() {
        logger.trace { "After starting servers" }
    }

    override fun beforeLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        logger.trace { "Before starting local TigerProxy" }
    }

    override fun afterLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        logger.trace { "After starting local TigerProxy" }
    }
}