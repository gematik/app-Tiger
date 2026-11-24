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
import com.example.tiger.CustomerLifecycleCallback
import com.example.tiger.CustomerLifecycleBase
import de.gematik.test.tiger.common.util.JandexIndexReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import org.jboss.jandex.Index
import org.jboss.jandex.IndexWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LifecycleManagerTest {

    @BeforeEach
    fun setup() {
        DummyLifecycleCallback.callbacksCalled = 0
        CustomerLifecycleCallback.calls.clear()
    }

    @AfterEach
    fun teardown() {
        DummyLifecycleCallback.callbacksCalled = 0
        CustomerLifecycleCallback.calls.clear()
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
        assertThat(CustomerLifecycleCallback.calls).containsExactly(
            "beforeConfiguration", "afterConfiguration", "beforeServers", "afterServers",
            "beforeProxy", "afterProxy"
        )
    }

    @Test
    fun `index contains production and test callbacks`() {
        val indexed = LifecycleManager()
            .findIndexedCallbacks(JandexIndexReader.classLoaders(LifecycleManager::class.java))
            .map { it.javaClass.name }
        assertThat(indexed).contains(
            LoggingCallback::class.java.name,
            DummyLifecycleCallback::class.java.name,
            CustomerLifecycleCallback::class.java.name
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["corrupt", "unsupported"])
    fun `unreadable index does not prevent discovery from valid indexes`(
        kind: String,
        @TempDir temp: Path
    ) {
        val valid = temp.resolve("valid.idx")
        Files.newOutputStream(valid).use {
            IndexWriter(it).write(
                Index.of(
                    LifecycleCallbacks::class.java,
                    CustomerLifecycleBase::class.java,
                    CustomerLifecycleCallback::class.java
                )
            )
        }
        val broken = temp.resolve("$kind.idx")
        if (kind == "corrupt") {
            Files.writeString(broken, "not an index")
        } else {
            val bytes = Files.readAllBytes(valid)
            bytes[4] = 127
            Files.write(broken, bytes)
        }

        val callbacks = LifecycleManager().findIndexedCallbacks(
            listOf(loader(broken.toUri().toURL(), valid.toUri().toURL()))
        )

        assertThat(callbacks.map { it.javaClass.name }).containsExactly(CustomerLifecycleCallback::class.java.name)
    }

    @Test
    fun `missing indexes produce no callbacks`() {
        assertThat(LifecycleManager().findIndexedCallbacks(listOf(loader()))).isEmpty()
    }

    private fun loader(vararg resources: URL): ClassLoader =
        object : ClassLoader(javaClass.classLoader) {
            override fun getResources(name: String) =
                if (name == JandexIndexReader.INDEX_RESOURCE) {
                    Collections.enumeration(resources.toList())
                } else {
                    super.getResources(name)
                }
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