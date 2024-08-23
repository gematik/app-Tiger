/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.testenvmgr.data.TigerConfigurationPropertyDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/global_configuration")
@Slf4j
public class TigerGlobalConfigurationController {

  @GetMapping("/all")
  public List<TigerConfigurationPropertyDto> getGlobalConfiguration() {
    return TigerGlobalConfiguration.exportConfiguration().entrySet().stream()
        .map(
            e ->
                TigerConfigurationPropertyDto.of(
                    e.getKey(), e.getValue().getRight(), e.getValue().getLeft().toString()))
        .sorted(Comparator.comparing(TigerConfigurationPropertyDto::getKey))
        .toList();
  }

  @GetMapping("/key/{keyPrefix}")
  public Map<String, String> loadSubsetOfConfiguration(
      @PathVariable("keyPrefix") String keyPrefix) {
    return TigerGlobalConfiguration.readMap(keyPrefix);
  }

  @PutMapping()
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  public void updateConfiguration(@RequestBody TigerConfigurationPropertyDto propertyToUpdate) {
    boolean exists =
        TigerGlobalConfiguration.readStringOptional(propertyToUpdate.getKey()).isPresent();

    if (exists) {
      TigerGlobalConfiguration.putValue(propertyToUpdate.getKey(), propertyToUpdate.getValue());
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  @DeleteMapping()
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  public void deleteConfiguration(@RequestBody TigerConfigurationPropertyDto propertyToDelete) {
    TigerGlobalConfiguration.deleteFromAllSources(
        new TigerConfigurationKey(propertyToDelete.getKey()));
  }

  @GetMapping(value = "/file")
  public ResponseEntity<String> getGlobalConfigurationFile() {
    val configurationAsYaml = getGlobalConfigurationAsYaml();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "application/yaml")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=global_configuration.yaml")
        .body(configurationAsYaml);
  }

  public String getGlobalConfigurationAsYaml() {
    val configurationAsMap =
        getGlobalConfiguration().stream()
            .collect(
                Collectors.toMap(
                    TigerConfigurationPropertyDto::getKey,
                    TigerConfigurationPropertyDto::getValue));
    return TigerSerializationUtil.toNestedYaml(configurationAsMap);
  }

  @PostMapping(value = "/file", consumes = "application/yaml")
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  public void importConfiguration(@RequestBody String configurationAsYaml) {
    log.debug("Importing: " + configurationAsYaml);
    var oldConfiguration = getGlobalConfiguration();
    try {
      TigerGlobalConfiguration.dangerouslyDeleteAllProperties();
      TigerGlobalConfiguration.readFromYaml(configurationAsYaml, ConfigurationValuePrecedence.RUNTIME_EXPORT, "");
    } catch (Exception e) {
      TigerGlobalConfiguration.dangerouslyDeleteAllProperties();
      oldConfiguration.forEach(
          p ->
              TigerGlobalConfiguration.putValue(
                  p.getKey(), p.getValue(), ConfigurationValuePrecedence.valueOf(p.getSource())));
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }
}
