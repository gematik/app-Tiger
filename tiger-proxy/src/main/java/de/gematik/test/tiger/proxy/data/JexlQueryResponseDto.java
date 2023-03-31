/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class JexlQueryResponseDto {

    private final boolean matchSuccessful;
    private final Map<String, Object> messageContext;
    private final String rbelTreeHtml;
    private final List<String> elements;

    private final String errorMessage;
}
