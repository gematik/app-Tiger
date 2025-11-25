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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LifecycleManagerTest {

    @BeforeEach
    fun setup() {
        DummyLifecycleCallback.callbacksCalled = 0
    }

    @AfterEach
    fun teardown() {
        DummyLifecycleCallback.callbacksCalled = 0
    }

    @Test
    fun `collecting callbacks finds Dummy callback class`() {
        val lifecycleManager = LifecycleManager()
        lifecycleManager.collectLifecycleCallbacks()
        assertThat(DummyLifecycleCallback.callbacksCalled).isEqualTo(0)
        lifecycleManager.beforeReadConfiguration()
        lifecycleManager.afterReadConfiguration()
        lifecycleManager.beforeServersStart()
        lifecycleManager.afterServersStart()
        lifecycleManager.beforeLocalTigerProxyStart(TigerProxyConfiguration())
        lifecycleManager.afterLocalTigerProxyStart(TigerProxyConfiguration())
        assertThat(DummyLifecycleCallback.callbacksCalled).isEqualTo(6)
    }
}

class DummyLifecycleCallback : LifecycleCallbacks {
    init {
        callbacksCalled = 0;
    }

    companion object {
        var callbacksCalled = 0;
    }

    override fun beforeReadConfiguration() {
        callbacksCalled++
    }

    override fun afterReadConfiguration() {
        callbacksCalled++
    }

    override fun beforeServersStart() {
        callbacksCalled++
    }

    override fun afterServersStart() {
        callbacksCalled++
    }

    override fun beforeLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        callbacksCalled++
    }

    override fun afterLocalTigerProxyStart(localTigerProxyConfiguration: TigerProxyConfiguration) {
        callbacksCalled++
    }
}