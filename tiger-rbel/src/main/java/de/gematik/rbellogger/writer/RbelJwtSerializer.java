/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.security.Key;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

public class RbelJwtSerializer implements RbelSerializer {

  @Override
  public byte[] render(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    return renderToString(node, rbelWriter).getBytes();
  }

  @Override
  public byte[] renderNode(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    return render(node, rbelWriter);
  }

  public String renderToString(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    final JsonWebSignature jws = new JsonWebSignature();

    ProviderContext context = new ProviderContext();
    context.getSuppliedKeyProviderContext().setGeneralProvider("BC");
    jws.setProviderContext(context);

    writeHeaderInJws(node.childNode("header"), jws, rbelWriter);

    jws.setPayloadBytes(
        rbelWriter
            .renderTree(
                node.childNode("body")
                    .orElseThrow(
                        () ->
                            new RbelSerializationException(
                                "Could not find body-node needed for JWT serialization in node '"
                                    + node.getKey()
                                    + "'!")))
            .getContent());
    jws.setKey(findSignerKey(node.childNode("signature"), rbelWriter));

    try {
      return jws.getCompactSerialization();
    } catch (JoseException e) {
      throw new RbelSerializationException("Error writing into Jwt", e);
    }
  }

  private Key findSignerKey(
      Optional<RbelContentTreeNode> signature, RbelWriterInstance rbelWriter) {
    if (signature.isEmpty()) {
      throw new RbelSerializationException(
          "Could not find signature-node needed for JWT serialization!");
    }
    final RbelContentTreeNode verifiedUsing =
        signature
            .get()
            .childNode("verifiedUsing")
            .orElseThrow(
                () ->
                    new RbelSerializationException(
                        "Could not find verifiedUsing-node needed for JWT serialization!"));
    final String keyName = verifiedUsing.getRawStringContent();
    final RbelKey rbelKey =
        rbelWriter
            .getRbelKeyManager()
            .findKeyByName(keyName)
            .filter(RbelKey::isPrivateKey)
            .or(() -> rbelWriter.getRbelKeyManager().findKeyByName("prk_" + keyName))
            .filter(RbelKey::isPrivateKey)
            .or(() -> rbelWriter.getRbelKeyManager().findKeyByName(keyName.replace("puk_", "prk_")))
            .filter(RbelKey::isPrivateKey)
            .orElseThrow(
                () ->
                    new RbelSerializationException("Could not find key named '" + keyName + "'!"));
    return rbelKey.getKey();
  }

  private void writeHeaderInJws(
      Optional<RbelContentTreeNode> headers, JsonWebSignature jws, RbelWriterInstance rbelWriter) {
    headers.map(RbelContentTreeNode::getChildNodes).stream()
        .flatMap(Collection::stream)
        .forEach(
            header -> {
              if (RbelJsonSerializer.isJsonArray(header)) {
                jws.setHeader(
                    header.getKey().orElseThrow(),
                    header.getChildNodes().stream()
                        .map(
                            childNode ->
                                new String(
                                    rbelWriter.renderTree(childNode).getContent(),
                                    childNode.getElementCharset()))
                        .collect(Collectors.toList()));
              } else {
                jws.setHeader(
                    header.getKey().orElseThrow(),
                    new String(
                        rbelWriter.renderTree(header).getContent(), header.getElementCharset()));
              }
            });
  }
}
