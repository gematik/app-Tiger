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
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelMessageRenderer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class RbelTcpIpMessageFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelMessageRenderer());
    }

    private final long sequenceNumber;
    private final RbelElement sender;
    private final RbelElement receiver;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("sender", sender)
            .with("receiver", receiver);
    }

    public RbelHostname getSenderHostname() {
        return sender.getFacetOrFail(RbelHostnameFacet.class).toRbelHostname();
    }

    public RbelHostname getReceiverHostname() {
        return receiver.getFacetOrFail(RbelHostnameFacet.class).toRbelHostname();
    }
}
