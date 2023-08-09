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

import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.writer.RbelContentType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class RbelContentTreeNode {

    private final RbelMultiMap<RbelContentTreeNode> childNodes;
    private Map<String, String> attributeMap = new HashMap<>();
    @Setter
    private Charset charset;
    @Getter
    @Setter
    private String key;
    @Getter
    @Setter
    private RbelContentType type;
    @Setter
    private RbelContentTreeNode parentNode;
    @Getter @Setter
    private byte[] content;

    public List<RbelContentTreeNode> childNodes() {
        return childNodes.values();
    }

    public void setChildNodes(List<RbelContentTreeNode> newChildNodes) {
        childNodes.clear();
        newChildNodes.forEach(node -> childNodes.put(node.getKey(), node));
    }

    public Map<String, String> attributes() {
        return attributeMap;
    }

    public Optional<Boolean> hasTypeOptional(RbelContentType typeToCheck) {
        return Optional.ofNullable(getType())
            .map(t -> t == typeToCheck);
    }

    public Optional<RbelContentTreeNode> getParentNode() {
        return Optional.ofNullable(parentNode);
    }

    public Optional<RbelContentTreeNode> childNode(String nodeKey) {
        return Optional.ofNullable(childNodes.get(nodeKey));
    }

    public String getContentAsString() {
        return new String(getContent(), getCharset());
    }

    public Charset getCharset() {
        return Optional.ofNullable(charset)
            .orElse(StandardCharsets.UTF_8);
    }
}
