/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TracingMessagePart {

  private String uuid;
  private int index;
  private int numberOfMessages;
  private byte[] data;
}
