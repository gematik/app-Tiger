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

package de.gematik.rbellogger.writer.tree;

import static de.gematik.rbellogger.writer.RbelContentTreeConverter.ENCODE_AS;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelBearerTokenFacet;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;

public class RbelBearerTokenElementToNodeConverter implements RbelElementToContentTreeNodeConverter {

    @Override
    public boolean shouldConvert(RbelElement target) {
        return target.hasFacet(RbelBearerTokenFacet.class);
    }

    @Override
    public RbelContentTreeNode convert(RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
        final RbelMultiMap<RbelContentTreeNode> map = new RbelMultiMap<RbelContentTreeNode>()
            .with("BearerToken", converter.convertNode(el.getFacetOrFail(RbelBearerTokenFacet.class).getBearerToken(), "BearerToken", context).get(0));
        final RbelStrictOrderContentTreeNode result = new RbelStrictOrderContentTreeNode(map);
        result.setType(context.readStringOptional(ENCODE_AS).map(RbelContentType::seekValueFor).orElse(RbelContentType.BEARER_TOKEN));
        return result;
    }
}
