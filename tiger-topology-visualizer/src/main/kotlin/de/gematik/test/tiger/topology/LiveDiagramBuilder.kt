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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration
import de.gematik.test.tiger.topology.deserialization.LightTigerConfigModel

// ──────────────────────────────────────────────────────────────────────────────
// Builds a topology diagram from the live TigerGlobalConfiguration.
//
// This reads the already-initialised configuration — it will NOT trigger
// initialisation. If TigerGlobalConfiguration has not been initialised yet,
// an empty diagram is returned.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Reads the current tiger configuration from [TigerGlobalConfiguration] and
 * builds a [ConfigurationDiagramModel].
 *
 * Safe to call at any time:
 * - If the configuration is already initialised → returns the full diagram.
 * - If not yet initialised → returns [ConfigurationDiagramModel.empty] without side effects.
 */
fun buildDiagramFromLiveConfiguration(): ConfigurationDiagramModel {
    if (!TigerGlobalConfiguration.isInitialized()) {
        return ConfigurationDiagramModel.empty()
    }

    val tigerConfig = TigerGlobalConfiguration.instantiateConfigurationBean(
        LightTigerConfigModel::class.java, "tiger"
    ).orElse(LightTigerConfigModel())

    return convertConfigurationToDiagramModel(
        config = tigerConfig,
        yamlResolver = FilesystemYamlResolver(),
        resolvePlaceholders = TigerGlobalConfiguration::resolvePlaceholders
    )
}

