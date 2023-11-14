/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@JsonInclude(Include.NON_NULL)
public class TigerRoute {

  @With private String id;
  private String from;
  private String to;
  @Builder.Default private boolean internalRoute = false;
  private boolean disableRbelLogging;
  private TigerBasicAuthConfiguration basicAuth;
}
