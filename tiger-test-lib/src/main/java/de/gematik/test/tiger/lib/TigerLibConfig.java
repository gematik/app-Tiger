/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@ToString
@JsonInclude(Include.NON_NULL)
public class TigerLibConfig {

  @Builder.Default private boolean rbelPathDebugging = false;
  @Builder.Default private boolean rbelAnsiColors = true;
  @Builder.Default private boolean addCurlCommandsForRaCallsToReport = true;
  @Builder.Default public boolean activateWorkflowUi = false;
  @Builder.Default public boolean startBrowser = true;
  @Builder.Default public boolean createRbelHtmlReports = true;
  @Builder.Default public boolean createRbelModificationReports = true;
  @Builder.Default public long pauseExecutionTimeoutSeconds = 18000L;
  @Builder.Default public TigerHttpClientConfig httpClientConfig = new TigerHttpClientConfig();
}
