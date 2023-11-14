/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ScenarioUpdate {

  private Map<String, StepUpdate> steps;
  private String description;
  private TestResult status;
  private List<String> exampleKeys;
  private Map<String, String> exampleList;
  private int variantIndex = -1;
}
