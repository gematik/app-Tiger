/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.ModificationDto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Data
@RequiredArgsConstructor
@Validated
@RestController
@Slf4j
public class TigerModificationController {

  private final TigerProxy tigerProxy;

  @PutMapping(value = "/modification", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ModificationDto addModification(@RequestBody ModificationDto addModificationDto) {
    final RbelModificationDescription modification =
        tigerProxy.addModificaton(
            RbelModificationDescription.builder()
                .name(addModificationDto.getName())
                .condition(addModificationDto.getCondition())
                .targetElement(addModificationDto.getTargetElement())
                .replaceWith(addModificationDto.getReplaceWith())
                .regexFilter(addModificationDto.getRegexFilter())
                .build());
    return ModificationDto.from(modification);
  }

  @GetMapping(value = "/modification", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ModificationDto> getModifications() {
    return tigerProxy.getModifications().stream().map(ModificationDto::from).toList();
  }

  @DeleteMapping(value = "/modification/{name}")
  public void deleteModification(@PathVariable @NotBlank String name) {
    tigerProxy.removeModification(name);
  }
}
