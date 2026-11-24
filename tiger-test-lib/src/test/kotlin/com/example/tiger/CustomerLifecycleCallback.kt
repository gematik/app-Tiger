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
package com.example.tiger

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration
import de.gematik.test.tiger.lifecycle.LifecycleCallbacks

abstract class CustomerLifecycleBase : LifecycleCallbacks

class CustomerLifecycleCallback : CustomerLifecycleBase() {
    companion object {
        val calls = mutableListOf<String>()
    }

    override fun beforeReadConfiguration() {
        calls.add("beforeConfiguration")
    }

    override fun afterReadConfiguration() {
        calls.add("afterConfiguration")
    }

    override fun beforeServersStart() {
        calls.add("beforeServers")
    }

    override fun afterServersStart() {
        calls.add("afterServers")
    }

    override fun beforeLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        calls.add("beforeProxy")
    }

    override fun afterLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        calls.add("afterProxy")
    }
}
