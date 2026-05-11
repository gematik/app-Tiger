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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class YamlResolverTest {

    /**
     * Uploaded resolver shall match on base file name and ignore paths.
     */
    @Nested
    inner class UploadedYamlResolverTest {

        @Test
        fun `should resolve uploaded file by exact file name`() {
            val uploaded = mapOf("docker-compose.yaml" to "services:\n  web:\n    image: nginx")
            val resolver = UploadedYamlResolver(uploaded)

            val result = resolver.resolve("docker-compose.yaml")

            assertThat(result).isEqualTo("services:\n  web:\n    image: nginx")
        }

        @Test
        fun `should resolve uploaded file by stripping directory path with forward slashes`() {
            val uploaded = mapOf("docker-compose.yaml" to "version: '3'")
            val resolver = UploadedYamlResolver(uploaded)

            val result = resolver.resolve("C:/tmp/project/config/docker-compose.yaml")

            assertThat(result).isEqualTo("version: '3'")
        }

        @Test
        fun `should resolve uploaded file by stripping directory path with backslashes`() {
            val uploaded = mapOf("docker-compose.yaml" to "version: '3'")
            val resolver = UploadedYamlResolver(uploaded)

            val result = resolver.resolve("C:\\tmp\\project\\config\\docker-compose.yaml")

            assertThat(result).isEqualTo("version: '3'")
        }

        @Test
        fun `should return null when file name is not in uploaded map`() {
            val uploaded = mapOf("other-file.yaml" to "content")
            val resolver = UploadedYamlResolver(uploaded)

            val result = resolver.resolve("docker-compose.yaml")

            assertThat(result).isNull()
        }

        @Test
        fun `should return null when uploaded map is empty`() {
            val resolver = UploadedYamlResolver(emptyMap())

            val result = resolver.resolve("any-file.yaml")

            assertThat(result).isNull()
        }

        @Test
        fun `should never read from filesystem even if file exists`(@TempDir tempDir: File) {
            val file = File(tempDir, "existing-file.yaml")
            file.writeText("filesystem content")

            val resolver = UploadedYamlResolver(emptyMap())

            val result = resolver.resolve(file.absolutePath)

            assertThat(result).isNull()
        }
    }

    /**
     * FilesystemYamlResolver shall look in file system for exact path
     */
    @Nested
    inner class FilesystemYamlResolverTest {

        @Test
        fun `should read file content from filesystem`(@TempDir tempDir: File) {
            val file = File(tempDir, "docker-compose.yaml")
            file.writeText("services:\n  db:\n    image: postgres")

            val resolver = FilesystemYamlResolver()

            val result = resolver.resolve(file.absolutePath)

            assertThat(result).isEqualTo("services:\n  db:\n    image: postgres")
        }

        @Test
        fun `should return null when file does not exist`() {
            val resolver = FilesystemYamlResolver()

            val result = resolver.resolve("/non/existent/path/docker-compose.yaml")

            assertThat(result).isNull()
        }

        @Test
        fun `should return null for empty path`() {
            val resolver = FilesystemYamlResolver()

            val result = resolver.resolve("")

            assertThat(result).isNull()
        }

        @Test
        fun `should read file with absolute path`(@TempDir tempDir: File) {
            val subDir = File(tempDir, "nested/dir")
            subDir.mkdirs()
            val file = File(subDir, "compose.yaml")
            file.writeText("version: '3'\nservices:\n  alpha:\n    image: alpha:latest")

            val resolver = FilesystemYamlResolver()

            val result = resolver.resolve(file.absolutePath)

            assertThat(result).isEqualTo("version: '3'\nservices:\n  alpha:\n    image: alpha:latest")
        }
    }
}


