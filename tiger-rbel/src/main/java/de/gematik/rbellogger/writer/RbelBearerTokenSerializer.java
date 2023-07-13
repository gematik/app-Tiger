/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
                    "Could not find BearerToken-node needed for BearerToken serialization in node '" + node.getKey() + "'!"))).getContent();

        return Arrays.concatenate("Bearer ".getBytes(StandardCharsets.UTF_8), bearerTokenContent);
    }
}
