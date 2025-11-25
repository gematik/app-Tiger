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

import de.gematik.test.tiger.lib.TigerDirector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables


class TigerDirectorStartWithCallbacksTest {

    @BeforeEach
    fun setup() {
        TigerDirector.testUninitialize();
        DummyLifecycleCallback.callbacksCalled = 0
    }

    @AfterEach
    fun teardown() {
        TigerDirector.testUninitialize();
        DummyLifecycleCallback.callbacksCalled = 0
    }

    @Test
    fun `starting tiger director should call lifecycle callbacks`() {
        assertThat(DummyLifecycleCallback.callbacksCalled).isEqualTo(0)
        withEnvironmentVariables("TIGER_TESTENV_CFGFILE", "src/test/resources/minimal_tiger.yaml").execute {
            TigerDirector.start()
        }
        assertThat(DummyLifecycleCallback.callbacksCalled).isEqualTo(6)
    }
}