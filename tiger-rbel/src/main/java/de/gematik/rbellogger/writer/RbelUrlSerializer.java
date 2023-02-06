/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class RbelUrlSerializer implements RbelSerializer {

    @Override
    public byte[] render(RbelContentTreeNode node, RbelWriter rbelWriter) {
        return renderToString(node, rbelWriter).getBytes();
    }

    public String renderToString(RbelContentTreeNode node, RbelWriter rbelWriter) {
        final String basicPath = node.childNode("basicPath")
            .map(RbelContentTreeNode::getContentAsString)
            .orElse("");
        final Optional<String> queryString = getQueryString(node.childNode("parameters"), rbelWriter);
        return basicPath + queryString
            .map(params -> "?" + params)
            .orElse("");
    }

    private Optional<String> getQueryString(Optional<RbelContentTreeNode> headers, RbelWriter rbelWriter) {
        return Optional.of(headers
                .map(RbelContentTreeNode::childNodes)
                .stream()
                .flatMap(Collection::stream)
                .map(header -> header.getKey() + "=" + new String(rbelWriter.renderTree(header), header.getCharset()))
                .collect(Collectors.joining("&")))
            .filter(s -> !s.isBlank());
    }
}
