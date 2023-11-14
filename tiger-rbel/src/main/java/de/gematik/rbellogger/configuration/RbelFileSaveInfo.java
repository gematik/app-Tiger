/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RbelFileSaveInfo {

  @Builder.Default private boolean writeToFile = false;
  @Builder.Default private String filename = "tiger-proxy.tgr";
  @Builder.Default private boolean clearFileOnBoot = false;
}
