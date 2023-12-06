/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.List;
import java.util.Optional;

/**
 * Enables the usage of the RbelPathExecutor. The methods are called by the RbelPathExecutor, but
 * not only. The description however focuses on that use-case
 */
public abstract class RbelPathAble {

  public abstract Optional<? extends RbelPathAble> getFirst(String key);

  public abstract RbelPathAble getParentNode();

  public abstract List<? extends RbelPathAble> getAll(String subkey);

  public abstract List<? extends RbelPathAble> getChildNodes();

  public abstract RbelMultiMap<? extends RbelPathAble> getChildNodesWithKey();

  public abstract Optional<String> getKey();

  public abstract String getRawStringContent();

  public abstract List<? extends RbelPathAble> findRbelPathMembers(String rbelPath);

  /**
   * Should return the list of search-relevant nodes. Normally this would be the identity (the
   * default implementation given here), but for virtual nodes (content-nodes in a rbel-tree for
   * example) that should not be part of the actual search-tree the child-nodes should be returned.
   */
  public List<RbelPathAble> descendToContentNodeIfAdvised() {
    return List.of(this);
  }

  /** Should this element be present in the final RbelPath results? */
  public boolean shouldElementBeKeptInFinalResult() {
    return true;
  }
}
