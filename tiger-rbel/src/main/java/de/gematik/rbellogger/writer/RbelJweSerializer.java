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
                .orElseThrow(() -> new RbelSerializationException("Could not find body-node needed for JWT serialization in node '" + node.getKey() + "'!"))));
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
                        .map(childNode -> new String(rbelWriter.renderTree(childNode), childNode.getCharset()))
                        .collect(Collectors.toList()));
                } else {
                    jwe.setHeader(header.getKey(), new String(rbelWriter.renderTree(header), header.getCharset()));
                }
            });
    }
}