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

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

public class RbelUriParameterWriter implements RbelElementWriter {

    @Override
    public boolean canWrite(RbelElement oldTargetElement) {
        return oldTargetElement.hasFacet(RbelUriParameterFacet.class);
    }

    @Override
    public byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
        final RbelUriParameterFacet uriFacet = oldTargetElement.getFacetOrFail(RbelUriParameterFacet.class);
        StringJoiner result = new StringJoiner("=");
        if (uriFacet.getKey() == oldTargetModifiedChild) {
            result.add(URLEncoder.encode(new String(newContent), StandardCharsets.UTF_8));
        } else {
            result.add(uriFacet.getKeyAsString());
        }

        if (uriFacet.getValue() == oldTargetModifiedChild) {
            result.add(URLEncoder.encode(new String(newContent), StandardCharsets.UTF_8));
        } else {
            result.add(uriFacet.getValue().getRawStringContent());
        }

        return result.toString().getBytes(oldTargetElement.getElementCharset());
    }
}
