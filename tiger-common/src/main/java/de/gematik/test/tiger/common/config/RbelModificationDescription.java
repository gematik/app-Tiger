/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RbelModificationDescription {

    @With
    private String name;
    @With
    private String condition;
    @With
    private String targetElement;
    @With
    private String replaceWith;
    @With
    private String regexFilter;
    @With
    private Integer deleteAfterNExecutions;
}
