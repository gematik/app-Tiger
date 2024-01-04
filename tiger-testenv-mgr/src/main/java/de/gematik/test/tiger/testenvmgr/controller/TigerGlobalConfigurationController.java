/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
  public Map<String, String> loadSubsetOfConfiguration(@PathVariable String keyPrefix) {
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
