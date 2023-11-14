/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import java.io.IOException;

public class TigerServerTypeSerializer extends JsonSerializer<TigerServerType> {
  @Override
  public void serialize(
      TigerServerType tigerServerType,
      JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(tigerServerType.value());
  }
}
