package de.gematik.test.tiger.proxy.controller;

import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.AddRouteDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Data
@RequiredArgsConstructor
@RestController("/configuration")
@Slf4j
public class TigerConfigurationController {

    private final TigerProxy tigerProxy;

    @PutMapping(value = "/route", produces = MediaType.APPLICATION_JSON_VALUE)
    public void addRoute(@RequestBody AddRouteDto addRouteDto) {
        log.info("Adding route from '{}' to '{}'", addRouteDto.getFrom(), addRouteDto.getTo());
        tigerProxy.addRoute(addRouteDto.getFrom(), addRouteDto.getTo());
    }
}
