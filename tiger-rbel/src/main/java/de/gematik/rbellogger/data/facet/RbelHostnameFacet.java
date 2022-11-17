/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMultiMap;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
public class RbelHostnameFacet implements RbelFacet {

    private final RbelElement port;
    private final RbelElement domain;
    private final Optional<RbelElement> bundledServerName;

    public static RbelElement buildRbelHostnameFacet(RbelElement parentNode, RbelHostname rbelHostname) {
        return buildRbelHostnameFacet(parentNode, rbelHostname, null);
    }

    public static RbelElement buildRbelHostnameFacet(RbelElement parentNode, RbelHostname rbelHostname, String bundledServerName) {
        if (rbelHostname == null) {
            return new RbelElement(null, parentNode);
        }
        final RbelElement result = new RbelElement(rbelHostname.toString().getBytes(StandardCharsets.UTF_8), parentNode);
        result.addFacet(RbelHostnameFacet.builder()
            .port(RbelElement.wrap(result, rbelHostname.getPort()))
            .domain(RbelElement.wrap(result, rbelHostname.getHostname()))
            .bundledServerName(Optional.ofNullable(bundledServerName)
                .map(bundledName -> RbelElement.wrap(parentNode, bundledName)))
            .build());
        return result;
    }

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
                .with("port", port)
                .with("domain", domain);
    }

    public String toString() {
        return bundledServerName
            .flatMap(el -> el.seekValue(String.class))
            .or(() -> domain.seekValue(String.class))
            .orElseThrow(() -> new RbelHostnameStructureException("Could not find domain-name!"))
            + port.seekValue(Integer.class)
            .map(port -> ":" + port).orElse("");
    }

    public RbelHostname toRbelHostname() {
        return RbelHostname.builder()
            .hostname(bundledServerName
                .flatMap(el -> el.seekValue(String.class))
                .or(() -> domain.seekValue(String.class))
                .orElseThrow())
            .port(port.seekValue(Integer.class).orElse(0))
            .build();
    }

    private class RbelHostnameStructureException extends RuntimeException {
        public RbelHostnameStructureException(String s) {
            super(s);
        }
    }
}
