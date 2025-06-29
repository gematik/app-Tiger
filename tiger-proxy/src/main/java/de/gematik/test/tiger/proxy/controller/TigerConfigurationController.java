/*
 *
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy.controller;

import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Data
@RequiredArgsConstructor
@Validated
@RestController
@Slf4j
public class TigerConfigurationController {

  private final TigerProxy tigerProxy;

  @PutMapping(value = "/route", consumes = MediaType.APPLICATION_JSON_VALUE)
  public TigerRouteDto addRoute(@RequestBody TigerRouteDto addRouteDto) {
    log.info("Adding route from '{}' to '{}'", addRouteDto.getFrom(), addRouteDto.getTo());
    val tigerRoute =
        tigerProxy.addRoute(
            TigerProxyRoute.builder().from(addRouteDto.getFrom()).to(addRouteDto.getTo()).build());
    return TigerRouteDto.create(tigerRoute);
  }

  @GetMapping(value = "/route", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<TigerRouteDto> getRoutes() {
    return tigerProxy.getRoutes().stream().map(TigerRouteDto::create).toList();
  }

  @DeleteMapping(value = "/route/{id}")
  public void deleteRoute(@PathVariable("id") String id) {
    tigerProxy.removeRoute(id);
  }
}
