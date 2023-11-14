/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class RbelUrlSerializer implements RbelSerializer {

  @Override
  public byte[] render(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    return renderToString(node, rbelWriter).getBytes();
  }

  @Override
  public byte[] renderNode(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    return render(node, rbelWriter);
  }

  public String renderToString(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    final String basicPath =
        node.childNode("basicPath").map(RbelContentTreeNode::getRawStringContent).orElse("");
    final Optional<String> queryString = getQueryString(node.childNode("parameters"), rbelWriter);
    return basicPath + queryString.map(params -> "?" + params).orElse("");
  }

  private Optional<String> getQueryString(
      Optional<RbelContentTreeNode> headers, RbelWriterInstance rbelWriter) {
    return Optional.of(
            headers.map(RbelContentTreeNode::getChildNodes).stream()
                .flatMap(Collection::stream)
                .map(
                    header ->
                        header.getKey().orElseThrow()
                            + "="
                            + new String(
                                rbelWriter.renderTree(header).getContent(),
                                header.getElementCharset()))
                .collect(Collectors.joining("&")))
        .filter(s -> !s.isBlank());
  }
}
