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
import de.gematik.rbellogger.data.RbelMultiMap;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class RbelHttpRequestFacet implements RbelFacet {

    private final RbelElement method;
    private final RbelElement path;
    private final RbelElement response;

    @Builder(toBuilder = true)
    public RbelHttpRequestFacet(RbelElement method, RbelElement path, RbelElement response) {
        this.method = method;
        this.path = path;
        this.response = response;
    }

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("method", method)
            .with("path", path);
    }

    public String getPathAsString() {
        return path.getRawStringContent();
    }
}
