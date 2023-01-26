/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;

public interface RbelElementToContentTreeNodeConverter {

    boolean shouldConvert(RbelElement target);

    RbelContentTreeNode convert(RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter);
}
