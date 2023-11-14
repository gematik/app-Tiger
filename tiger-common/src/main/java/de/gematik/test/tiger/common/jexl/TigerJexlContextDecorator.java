/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import java.util.Optional;

/**
 * Interface which allows the decoration (adding more items to) a JEXL-Context that is being
 * created.
 */
public interface TigerJexlContextDecorator {

  /**
   * Decorate the given context using the information provided. This is highly contextual since this
   * can be called with different kinds of elements (hence only Object)
   *
   * @param element The current element (CAN be of type RbelElement. To avoid circular dependencies
   *     it is taken as Object)
   * @param key The key, if given, of the current element
   * @param context The context that is being build
   */
  void decorate(Object element, Optional<String> key, TigerJexlContext context);
}
