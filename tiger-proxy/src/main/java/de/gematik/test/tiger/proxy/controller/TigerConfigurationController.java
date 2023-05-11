/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.proxy.controller;

import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;
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
public class TigerConfigurationController {

    private final TigerProxy tigerProxy;

    @PutMapping(value = "/route", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TigerRouteDto addRoute(@RequestBody TigerRouteDto addRouteDto) {
        log.info("Adding route from '{}' to '{}'", addRouteDto.getFrom(), addRouteDto.getTo());
        final TigerRoute tigerRoute = tigerProxy.addRoute(TigerRoute.builder()
                .from(addRouteDto.getFrom())
                .to(addRouteDto.getTo())
                .build());
        return TigerRouteDto.from(tigerRoute);
    }

    @GetMapping(value = "/route", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TigerRouteDto> getRoutes() {
        return tigerProxy.getRoutes().stream()
            .map(TigerRouteDto::from)
            .collect(Collectors.toList());
    }

    @DeleteMapping(value = "/route/{id}")
    public void deleteRoute(@PathVariable @NotBlank String id) {
        tigerProxy.removeRoute(id);
    }
}
