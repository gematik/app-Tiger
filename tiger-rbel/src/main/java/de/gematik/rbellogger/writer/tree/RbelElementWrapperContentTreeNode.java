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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

public class RbelElementWrapperContentTreeNode extends RbelContentTreeNode {

    public static RbelElementWrapperContentTreeNode constructFromRbelElement(RbelElement source, TigerConfigurationLoader conversionContext) {
        var result = new RbelElementWrapperContentTreeNode();
        result.setContent(TokenSubstituteHelper.substitute(source.getRawStringContent(), conversionContext)
            .getBytes());
        return result;
    }

    public static RbelElementWrapperContentTreeNode constructFromValueElement(RbelElement source, TigerConfigurationLoader conversionContext) {
        var result = new RbelElementWrapperContentTreeNode();
        result.setContent(TokenSubstituteHelper.substitute(source.printValue().orElseGet(source::getRawStringContent), conversionContext)
            .getBytes());
        return result;
    }

    private RbelElementWrapperContentTreeNode() {
        super(new RbelMultiMap<>());
    }

    @Override
    public List<RbelContentTreeNode> childNodes() {
        return List.of();
    }

    @Override
    public String toString() {
        return "wrappernode:{<node child nodes>, content=" + new String(getContent()) + "}";
    }
}