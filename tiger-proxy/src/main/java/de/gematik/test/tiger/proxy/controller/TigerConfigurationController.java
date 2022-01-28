/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotBlank;
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
