/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Builder
@Slf4j
@AllArgsConstructor
public class TigerTemplateSource {

    private final String templateName;
    private final List<TigerConfigurationKeyString> targetPath;
    private final Map<TigerConfigurationKey, String> values;

    public List<TigerConfigurationKey> applyToAllApplicable(final AbstractTigerConfigurationSource tigerConfigurationSource,
                                                                        final Map<TigerConfigurationKey, String> finalValues) {
        List<TigerConfigurationKey> appliedTemplateKeys = new ArrayList<>();
        var priorKeys = new HashSet<>(finalValues.keySet());
        tigerConfigurationSource.getValues().entrySet().stream()
            .filter(entry -> entry.getKey().size() == targetPath.size() + 1 + 1) // basePath + serverName + "template"
            .filter(entry -> entry.getKey().subList(0, targetPath.size()).equals(targetPath))
            .filter(entry -> entry.getKey().get(targetPath.size() + 1).getValue().equalsIgnoreCase("template"))
            .filter(entry -> entry.getValue().equals(templateName))
            .forEach(templateSelectionEntry -> {
                for (Map.Entry<TigerConfigurationKey, String> valueEntry : values.entrySet()) {
                    var newKey = new TigerConfigurationKey();
                    newKey.addAll(targetPath);
                    newKey.add(templateSelectionEntry.getKey().get(targetPath.size())); //serverName
                    newKey.addAll(valueEntry.getKey());
                    if (priorKeys.stream()
                        .anyMatch(existingKey -> shouldBeReplaced(existingKey, newKey, targetPath))) {
                        continue;
                    }
                    if (!finalValues.containsKey(newKey)) {
                        finalValues.put(newKey, valueEntry.getValue());
                    }
                }
                appliedTemplateKeys.add(templateSelectionEntry.getKey());
            });
        return appliedTemplateKeys;
    }

    private boolean shouldBeReplaced(List<TigerConfigurationKeyString> existingKey, List<TigerConfigurationKeyString> newKey, List<TigerConfigurationKeyString> basePath) {
        /*
        existingKey: servers.foobar.sources.0

        newKey:      servers.foobar.sources.0
        or           servers.foobar.sources.1
         */
        /*
        existingKey: servers.foobar.sources.0.foo.bar

        newKey:      servers.foobar.sources.0.schmoo.loo.koo
        or           servers.foobar.sources.1.foo.bar
         */
        boolean pathContainedNumber = false;
        for (int i = basePath.size() + 1; i < Math.min(existingKey.size(), newKey.size()); i++) {
            final TigerConfigurationKeyString existingKeyPart = existingKey.get(i);
            final TigerConfigurationKeyString newKeyPart = newKey.get(i);
            if (existingKeyPart.equals(newKeyPart)) {
                pathContainedNumber |= NumberUtils.isDigits(newKeyPart.getValue());
                continue;
            }
            if (pathContainedNumber || NumberUtils.isDigits(newKeyPart.getValue())) {
                log.info("Skipping {} due to {}", newKey.stream()
                    .map(TigerConfigurationKeyString::getValue)
                    .collect(Collectors.joining(",")), existingKey.stream()
                    .map(TigerConfigurationKeyString::getValue)
                    .collect(Collectors.joining(",")));
                return true;
            } else {
                return false;
            }
        }
        return false;
    }
}
