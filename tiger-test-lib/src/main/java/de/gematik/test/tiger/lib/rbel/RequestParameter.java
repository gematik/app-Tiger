/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Builder
@Getter
public class RequestParameter {

    private String path;
    private String rbelPath;
    private String value;
    private final boolean startFromLastRequest;
    private final boolean filterPreviousRequest;

    public RequestParameter resolvePlaceholders()  {
        if (StringUtils.isNotEmpty(path)) {
            path = TigerGlobalConfiguration.resolvePlaceholders(path);
        }
        if (StringUtils.isNotEmpty(rbelPath)) {
            rbelPath = TigerGlobalConfiguration.resolvePlaceholders(rbelPath);
        }
        if (StringUtils.isNotEmpty(value)) {
            value = TigerGlobalConfiguration.resolvePlaceholders(value);
        }
        return this;
    }
}
