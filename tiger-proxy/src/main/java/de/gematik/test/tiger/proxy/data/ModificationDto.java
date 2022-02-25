/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.modifier.RbelModificationDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
@AllArgsConstructor
@Data
public class ModificationDto {
    
    private String name;
    private String condition;
    private String targetElement;
    private String replaceWith;
    private String regexFilter;

    public static ModificationDto from(RbelModificationDescription modification) {
        return ModificationDto.builder()
            .name(modification.getName())
            .condition(modification.getCondition())
            .targetElement(modification.getTargetElement())
            .replaceWith(modification.getReplaceWith())
            .regexFilter(modification.getRegexFilter())
            .build();
    }
}
