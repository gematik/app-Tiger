/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.List;

public class RbelElementWrapperContentTreeNode extends RbelContentTreeNode {

    private final RbelElement source;

    public RbelElementWrapperContentTreeNode(RbelElement source, TigerConfigurationLoader conversionContext) {
        super(new RbelMultiMap<>());
        setContent(TokenSubstituteHelper.substitute(source.getRawStringContent(), conversionContext)
            .getBytes());
        this.source = source;
    }

    @Override
    public List<RbelContentTreeNode> childNodes() {
        return List.of();
    }

    @Override
    public String toString() {
        return "wrappernode:{<node child nodes>, content=" + new String(getContent()) + "}";
    }
}
