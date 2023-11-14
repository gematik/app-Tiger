/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.data.RbelMultiMap;

public class RbelStrictOrderContentTreeNode extends RbelContentTreeNode {

  public RbelStrictOrderContentTreeNode(
      RbelMultiMap<RbelContentTreeNode> childNodes, byte[] content) {
    super(childNodes, content);
  }
}
