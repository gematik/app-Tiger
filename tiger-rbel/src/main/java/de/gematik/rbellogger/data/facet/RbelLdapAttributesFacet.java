package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;

import java.util.Optional;

import static j2html.TagCreator.*;
import static j2html.TagCreator.b;

public class RbelLdapAttributesFacet extends RbelMultiMap<RbelElement> implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelLdapAttributesFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            var div = div();
            div.with(h2().withClass("title").withText("LDAP attributes: "));
            var facet = element.getFacet(RbelLdapAttributesFacet.class).orElseThrow();
            div.with(
                facet.getChildElements().stream()
                    .map(
                        child ->
                            p().with(b().withText(child.getKey()).withText(": "))
                                .withText(child.getValue().printValue().orElse("")))
                    .toList());
            return div;
          }
        });
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return this;
  }
}
