/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import static de.gematik.rbellogger.writer.RbelContentTreeConverter.ENCODE_AS;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelBearerTokenFacet;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;

public class RbelBearerTokenElementToNodeConverter implements RbelElementToContentTreeNodeConverter {

    @Override
    public boolean shouldConvert(RbelElement target) {
        return target.hasFacet(RbelBearerTokenFacet.class);
    }

    @Override
    public RbelContentTreeNode convert(RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
        final RbelMultiMap<RbelContentTreeNode> map = new RbelMultiMap<RbelContentTreeNode>()
            .with("BearerToken", converter.convertNode(el.getFacetOrFail(RbelBearerTokenFacet.class).getBearerToken(), "BearerToken", context).get(0));
        final RbelStrictOrderContentTreeNode result = new RbelStrictOrderContentTreeNode(map, el.getRawContent());
        result.setType(context.readStringOptional(ENCODE_AS).map(RbelContentType::seekValueFor).orElse(RbelContentType.BEARER_TOKEN));
        return result;
    }
}
