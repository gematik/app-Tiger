/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
public class TigerProxyReportConfiguration {

    @Builder.Default
    private String filenamePattern = "tiger-report-${GEMATIKACCOUNT}-${DATE}-${TIME}.zip";
    @Builder.Default
    private String uploadUrl = "UNDEFINED";
    private String username;
    private String password;
}
