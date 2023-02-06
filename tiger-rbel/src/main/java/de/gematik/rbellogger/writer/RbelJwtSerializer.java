/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.awt.List;
import java.security.Key;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

public class RbelJwtSerializer implements RbelSerializer {

    @Override
    public byte[] render(RbelContentTreeNode node, RbelWriter rbelWriter) {
        return renderToString(node, rbelWriter).getBytes();
    }

    public String renderToString(RbelContentTreeNode node, RbelWriter rbelWriter) {
        final JsonWebSignature jws = new JsonWebSignature();

        ProviderContext context = new ProviderContext();
        context.getSuppliedKeyProviderContext().setGeneralProvider("BC");
        jws.setProviderContext(context);

        writeHeaderInJws(node.childNode("header"), jws, rbelWriter);

        jws.setPayloadBytes(rbelWriter.renderTree(
            node.childNode("body")
                .orElseThrow(() -> new RbelSerializationException("Could not find body-node needed for JWT serialization in node '" + node.getKey() + "'!"))));
        jws.setKey(findSignerKey(node.childNode("signature"), rbelWriter));

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RbelSerializationException("Error writing into Jwt", e);
        }
    }

    private Key findSignerKey(Optional<RbelContentTreeNode> signature, RbelWriter rbelWriter) {
        if (signature.isEmpty()) {
            throw new RbelSerializationException("Could not find signature-node needed for JWT serialization!");
        }
        final RbelContentTreeNode verifiedUsing = signature.get().childNode("verifiedUsing")
            .orElseThrow(() -> new RbelSerializationException("Could not find verifiedUsing-node needed for JWT serialization!"));
        final String keyName = verifiedUsing.getContentAsString();
        final RbelKey rbelKey = rbelWriter.getRbelKeyManager().findKeyByName(keyName)
            .or(() -> rbelWriter.getRbelKeyManager().findKeyByName("prk_" + keyName))
            .orElseThrow(() -> new RbelSerializationException("Could not find key named '" + keyName + "'!"));
        return rbelKey.getKey();
    }

    private void writeHeaderInJws(Optional<RbelContentTreeNode> headers, JsonWebSignature jws, RbelWriter rbelWriter) {
        headers
            .map(RbelContentTreeNode::childNodes)
            .stream()
            .flatMap(Collection::stream)
            .forEach(header -> {
                if (RbelJsonSerializer.isJsonArray(header)) {
                    jws.setHeader(header.getKey(), header.childNodes().stream()
                        .map(childNode -> new String(rbelWriter.renderTree(childNode), childNode.getCharset()))
                        .collect(Collectors.toList()));
                } else {
                    jws.setHeader(header.getKey(), new String(rbelWriter.renderTree(header), header.getCharset()));
                }
            });
    }
}
