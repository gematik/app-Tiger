package de.gematik.test.tiger.common.data.config.tigerproxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder(builderMethodName = "newNote")
@JsonInclude(Include.NON_NULL)
public class TigerProxyNoteCommand {

  private String message;
  private String jexlCriterion;
}
