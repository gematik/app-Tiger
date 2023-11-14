/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepUpdate {

  private String description;
  private TestResult status;
  private int stepIndex = -1;
  private List<MessageMetaDataDto> rbelMetaData = new ArrayList<>();
}
