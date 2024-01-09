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
    return buildProperties.map(BuildProperties::getVersion).orElse("<unknown version>");
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
