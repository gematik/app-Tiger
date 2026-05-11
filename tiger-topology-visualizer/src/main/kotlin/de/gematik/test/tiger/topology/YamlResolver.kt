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

//When in standalone mode, we want to read the compose yaml files from the uploaded file list.
//When integrated in the Workflow UI we want to read them from the file system relative to the yaml file.
//We implement here both alternatives and use the appropriate one in each case

fun interface YamlResolver {
    fun resolve(path: String): String?
}

class UploadedYamlResolver(private val uploaded: Map<String, String>) : YamlResolver {
    override fun resolve(path: String): String? = uploaded[extractFileName(path)]
}

class FilesystemYamlResolver : YamlResolver {
    override fun resolve(path: String): String? = runCatching { java.io.File(path).readText() }.getOrNull()
}

