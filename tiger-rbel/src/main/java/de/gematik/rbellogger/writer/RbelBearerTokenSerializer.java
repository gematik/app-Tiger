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

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.util.Arrays;

public class RbelBearerTokenSerializer implements RbelSerializer {

    @Override
    public byte[] render(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
        final byte[] bearerTokenContent = rbelWriter.renderTree(
            node.childNode("BearerToken")
                .orElseThrow(() -> new RbelSerializationException(
                    "Could not find BearerToken-node needed for BearerToken serialization in node '" + node.getKey() + "'!")));

        return Arrays.concatenate("Bearer ".getBytes(StandardCharsets.UTF_8), bearerTokenContent);
    }
}
