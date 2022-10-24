/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(access = AccessLevel.PUBLIC)
@Getter
public class RbelBundleCriterion {

    private final String bundledServerName;
    private final List<String> sender;
    private final List<String> receiver;
}
