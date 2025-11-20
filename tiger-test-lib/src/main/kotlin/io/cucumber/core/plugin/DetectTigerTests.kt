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

package io.cucumber.core.plugin

import io.cucumber.core.options.Constants
import org.junit.platform.commons.util.AnnotationUtils
import org.junit.platform.engine.ConfigurationParameters
import org.junit.platform.engine.DiscoverySelector
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.suite.api.ConfigurationParameter
import kotlin.jvm.optionals.getOrNull

fun isATigerTest(request: LauncherDiscoveryRequest): Boolean {
    return containsTigerPluginConfiguration(request.configurationParameters) || driverClassHasPluginAnnotation(request)
}


fun containsTigerPluginConfiguration(configuration: ConfigurationParameters): Boolean {
    val pluginProperty = configuration[Constants.PLUGIN_PROPERTY_NAME].getOrNull()
    return pluginProperty?.contains(TigerSerenityReporterPlugin::class.java.name) ?: false
}

private fun driverClassHasPluginAnnotation(request: LauncherDiscoveryRequest): Boolean {
    val discoverySelectors = request.getSelectorsByType(DiscoverySelector::class.java).toList()
    if (discoverySelectors.isEmpty()) {
        return false
    }
    val identifiers = discoverySelectors.mapNotNull { it.toIdentifier().getOrNull() }
    val classes = identifiers.mapNotNull { it.value }.mapNotNull { getClassForName(it) }

    return classes.any { classHasTigerPlugin(it) }
}

private fun classHasTigerPlugin(clazz: Class<*>): Boolean {
    val params = AnnotationUtils.findRepeatableAnnotations(clazz, ConfigurationParameter::class.java).toList()
    return params.any { isTigerPlugin(it) }
}

private fun isTigerPlugin(parameter: ConfigurationParameter): Boolean {
    return Constants.PLUGIN_PROPERTY_NAME == parameter.key && parameter.value.contains(TigerSerenityReporterPlugin::class.java.name)
}

private fun getClassForName(it: String): Class<*>? {
    try {
        return Class.forName(it)
    } catch (ex: ClassNotFoundException) {
        return null;
    }
}
