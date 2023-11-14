/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CfgTemplates {
  private List<CfgServer> templates;
}
