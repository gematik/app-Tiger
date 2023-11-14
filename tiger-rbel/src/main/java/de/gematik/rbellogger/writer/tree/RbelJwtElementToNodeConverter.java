/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import static de.gematik.rbellogger.writer.RbelContentTreeConverter.ENCODE_AS;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.elements.RbelJwtSignature;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;

public class RbelJwtElementToNodeConverter implements RbelElementToContentTreeNodeConverter {

  @Override
  public boolean shouldConvert(RbelElement target) {
    return target.hasFacet(RbelJwtFacet.class);
  }

  @Override
  public RbelContentTreeNode convert(
      RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
    final RbelMultiMap<RbelContentTreeNode> map =
        new RbelMultiMap<RbelContentTreeNode>()
            .with(
                "header",
                converter
                    .convertNode(
                        el.getFacetOrFail(RbelJwtFacet.class).getHeader(), "header", context)
                    .get(0))
            .with(
                "body",
                converter
                    .convertNode(el.getFacetOrFail(RbelJwtFacet.class).getBody(), "body", context)
                    .get(0))
            .with(
                "signature",
                convertSignature(
                    el.getFacetOrFail(RbelJwtFacet.class).getSignature(), context, converter));
    final RbelStrictOrderContentTreeNode result =
        new RbelStrictOrderContentTreeNode(map, el.getRawContent());
    result.setType(
        context
            .readStringOptional(ENCODE_AS)
            .map(RbelContentType::seekValueFor)
            .orElse(RbelContentType.JWT));
    return result;
  }

  private RbelContentTreeNode convertSignature(
      RbelElement signature, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
    final RbelMultiMap<RbelContentTreeNode> content = new RbelMultiMap<>();
    if (signature.getFacetOrFail(RbelJwtSignature.class).getVerifiedUsing() != null) {
      content.with(
          "verifiedUsing",
          RbelElementWrapperContentTreeNode.constructFromValueElement(
              signature.getFacetOrFail(RbelJwtSignature.class).getVerifiedUsing(),
              context,
              converter.getJexlContext()));
    }
    final RbelStrictOrderContentTreeNode result =
        new RbelStrictOrderContentTreeNode(content, signature.getRawContent());
    result.setKey("signature");
    return result;
  }
}
