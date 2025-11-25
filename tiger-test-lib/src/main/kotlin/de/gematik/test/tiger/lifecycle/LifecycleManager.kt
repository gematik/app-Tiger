/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.test.tiger.lifecycle

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration
import org.reflections.Reflections


class LifecycleManager : ILifecycleManager {
    private var callbacks: List<LifecycleCallbacks> = emptyList()

    fun collectLifecycleCallbacks() {
        //find via reflection all classes implementing LifecycleCallbacks and instantiate them
        val reflections = Reflections("de.gematik.test.tiger.lifecycle")
        val callbackClasses = reflections.getSubTypesOf(LifecycleCallbacks::class.java).toSet()

        callbacks = callbackClasses.mapNotNull { clazz ->
            clazz.getDeclaredConstructor().newInstance()
        }
    }

    override fun beforeReadConfiguration() {
        callbacks.forEach { it.beforeReadConfiguration() }
    }

    override fun afterReadConfiguration() {
        callbacks.forEach { it.afterReadConfiguration() }
    }

    override fun beforeServersStart() {
        callbacks.forEach { it.beforeServersStart() }
    }

    override fun afterServersStart() {
        callbacks.forEach { it.afterServersStart() }
    }

    override fun beforeLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        callbacks.forEach { it.beforeLocalTigerProxyStart(localTigerProxyConfiguration) }
    }

    override fun afterLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        callbacks.forEach { it.afterLocalTigerProxyStart(localTigerProxyConfiguration) }
    }

}