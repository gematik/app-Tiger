/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.renderer;

import de.gematik.rbellogger.data.RbelElement;
import j2html.tags.ContainerTag;
import java.util.Optional;

public interface RbelHtmlFacetRenderer {

    boolean checkForRendering(RbelElement element);

    ContainerTag performRendering(RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit renderingToolkit);

    default int order() {
        return 0;
    }
}
