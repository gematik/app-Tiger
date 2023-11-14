/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;

public interface RbelSerializer {
  byte[] render(RbelContentTreeNode treeRootNode, RbelWriterInstance rbelWriter);

  byte[] renderNode(RbelContentTreeNode treeRootNode, RbelWriterInstance rbelWriter);
}
