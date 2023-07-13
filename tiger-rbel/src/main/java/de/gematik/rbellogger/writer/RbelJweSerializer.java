/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.security.Key;
import java.security.Security;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

public class RbelJweSerializer implements RbelSerializer {

    @Override
    public byte[] render(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
        return renderToString(node, rbelWriter).getBytes();
    }

    public String renderToString(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
        final JsonWebEncryption jwe = new JsonWebEncryption();

        ProviderContext context = new ProviderContext();
        context.getGeneralProviderContext().setGeneralProvider("BC");
        jwe.setProviderContext(context);

        writeHeaderInJwe(node.childNode("header"), jwe, rbelWriter);

        jwe.setPlaintext(rbelWriter.renderTree(
            node.childNode("body")
                .orElseThrow(() -> new RbelSerializationException("Could not find body-node needed for JWT serialization in node '" + node.getKey() + "'!"))).getContent());
        jwe.setKey(findSignerKey(node.childNode("encryptionInfo"), rbelWriter));

        try {
            return jwe.getCompactSerialization();
        } catch (JoseException e) {
            throw new RbelSerializationException("Error writing into Jwt", e);
        }
    }

    private Key findSignerKey(Optional<RbelContentTreeNode> signature, RbelWriterInstance rbelWriter) {
        if (signature.isEmpty()) {
            throw new RbelSerializationException("Could not find signature-node needed for JWT serialization!");
        }
        return signature.get().childNode("decryptedUsingKeyWithId")
            .map(RbelContentTreeNode::getContentAsString)
            .map(keyName -> rbelWriter.getRbelKeyManager().findKeyByName(keyName)
                .or(() -> rbelWriter.getRbelKeyManager().findKeyByName("prk_" + keyName))
                .orElseThrow(() -> new RbelSerializationException("Could not find key named '" + keyName + "'!")))
            .map(RbelKey::getKey)
            .or(() -> signature.get().childNode("decryptedUsingKey")
                .map(RbelContentTreeNode::getContentAsString)
                .map(Base64.getUrlDecoder()::decode)
                .map(keyBytes -> new SecretKeySpec(keyBytes, "AES")))
            .orElseThrow(() -> new RbelSerializationException("Unable to find key!"));
    }

    private void writeHeaderInJwe(Optional<RbelContentTreeNode> headers, JsonWebEncryption jwe, RbelWriterInstance rbelWriter) {
        headers
            .map(RbelContentTreeNode::childNodes)
            .stream()
            .flatMap(Collection::stream)
            .forEach(header -> {
                if (RbelJsonSerializer.isJsonArray(header)) {
                    jwe.setHeader(header.getKey(), header.childNodes().stream()
                        .map(childNode -> new String(rbelWriter.renderTree(childNode).getContent(), childNode.getCharset()))
                        .collect(Collectors.toList()));
                } else {
                    jwe.setHeader(header.getKey(), new String(rbelWriter.renderTree(header).getContent(), header.getCharset()));
                }
            });
    }
}
