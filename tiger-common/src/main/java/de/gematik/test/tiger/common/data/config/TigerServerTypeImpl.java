/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import java.io.IOException;
import java.lang.annotation.Annotation;

public class TigerServerTypeImpl extends StdScalarDeserializer<TigerServerType>
    implements TigerServerType {

  private String val;

  public TigerServerTypeImpl() {
    super(TigerServerType.class);
    val = "UNSET";
  }

  public TigerServerTypeImpl(String typeToken) {
    super(TigerServerType.class);
    val = typeToken;
  }

  @Override
  public String value() {
    return val;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return TigerServerType.class;
  }

  @Override
  public TigerServerType deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    return new TigerServerTypeImpl(jsonParser.getValueAsString());
  }
}
