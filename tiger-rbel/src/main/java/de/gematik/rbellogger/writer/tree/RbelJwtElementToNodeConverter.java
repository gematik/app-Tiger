/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import static de.gematik.rbellogger.writer.RbelContentTreeConverter.ENCODE_AS;
import com.google.gson.JsonPrimitive;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.elements.RbelJwtSignature;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.data.facet.RbelNestedFacet;
import de.gematik.rbellogger.modifier.RbelJwtWriter.JwtUpdateException;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class RbelJwtElementToNodeConverter implements RbelElementToContentTreeNodeConverter {

    @Override
    public boolean shouldConvert(RbelElement target) {
        return target.hasFacet(RbelJwtFacet.class);
    }

    @Override
    public RbelContentTreeNode convert(RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
        final RbelMultiMap<RbelContentTreeNode> map = new RbelMultiMap<RbelContentTreeNode>()
            .with("header", converter.convertNode(el.getFacetOrFail(RbelJwtFacet.class).getHeader(), "header", context).get(0))
            .with("body", converter.convertNode(el.getFacetOrFail(RbelJwtFacet.class).getBody(), "body", context).get(0))
            .with("signature", convertSignature(el.getFacetOrFail(RbelJwtFacet.class).getSignature(), context));
        final RbelStrictOrderContentTreeNode result = new RbelStrictOrderContentTreeNode(map);
        result.setType(context.readStringOptional(ENCODE_AS).map(RbelContentType::seekValueFor).orElse(RbelContentType.JWT));
        return result;
    }

    private RbelContentTreeNode convertSignature(RbelElement signature, TigerConfigurationLoader context) {
        final RbelMultiMap<RbelContentTreeNode> content = new RbelMultiMap<>();
        if (signature.getFacetOrFail(RbelJwtSignature.class).getVerifiedUsing() != null) {
            content.with("verifiedUsing", RbelElementWrapperContentTreeNode.constructFromValueElement(
                signature.getFacetOrFail(RbelJwtSignature.class).getVerifiedUsing(),
                context));
        }
        final RbelStrictOrderContentTreeNode result = new RbelStrictOrderContentTreeNode(content);
        result.setKey("signature");
        return result;
    }
}
