/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerproxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.io.Serializable;
import java.util.List;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@JsonInclude(Include.NON_NULL)
public class TigerRoute implements Serializable {

  @With private String id;
  private String from;
  private String to;
  @Builder.Default private boolean internalRoute = false;
  private boolean disableRbelLogging;
  private TigerBasicAuthConfiguration basicAuth;
  private List<String> criterions;
}
