/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.spring_utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TigerBuildPropertiesService {

    private final Optional<BuildProperties> buildProperties;

    public String tigerVersionAsString() {
        return buildProperties
            .map(BuildProperties::getVersion)
            .orElse("<unknown version>");
    }

    public String tigerBuildDateAsString() {
        return buildProperties
            .map(BuildProperties::getTime)
            .map(t -> t.atZone(ZoneId.systemDefault()))
            .map(ZonedDateTime::toLocalDate)
            .map(LocalDate::toString)
            .orElse("-?-");
    }
}
