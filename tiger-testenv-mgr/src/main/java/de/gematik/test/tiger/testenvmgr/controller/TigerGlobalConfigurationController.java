/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.data.TigerConfigurationPropertyDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/global_configuration")
public class TigerGlobalConfigurationController {

  @GetMapping()
  public List<TigerConfigurationPropertyDto> getGlobalConfiguration() {
    return TigerGlobalConfiguration.exportConfiguration().entrySet().stream()
        .map(
            e ->
                TigerConfigurationPropertyDto.of(
                    e.getKey(), e.getValue().getRight(), e.getValue().getLeft().toString()))
        .sorted(Comparator.comparing(TigerConfigurationPropertyDto::getKey))
        .toList();
  }

  @GetMapping("/{keyPrefix}")
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
}
