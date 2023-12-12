/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.pki;

import static de.gematik.test.tiger.common.config.TigerConfigurationLoader.TIGER_CONFIGURATION_ATTRIBUTE_KEY;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.TextNode;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity.TigerPkiIdentityDeserializer;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity.TigerPkiIdentitySerializer;
import java.io.IOException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(value = {"certificate", "privateKey", "keyId", "certificateChain"})
@JsonDeserialize(using = TigerPkiIdentityDeserializer.class)
@JsonSerialize(using = TigerPkiIdentitySerializer.class)
public class TigerConfigurationPkiIdentity extends TigerPkiIdentity {
  private String fileLoadingInformation;

  public TigerConfigurationPkiIdentity(String fileLoadingInformation) {
    super(fileLoadingInformation);
    this.fileLoadingInformation = fileLoadingInformation;
  }

  public static class TigerPkiIdentityDeserializer
      extends JsonDeserializer<TigerConfigurationPkiIdentity> {

    @Override
    public TigerConfigurationPkiIdentity deserialize(JsonParser p, DeserializationContext ctxt) {
      try {
        final String value = ((TextNode) p.readValueAsTree()).asText();
        final String substitutedValue =
            TokenSubstituteHelper.substitute(
                value,
                (TigerConfigurationLoader) ctxt.getAttribute(TIGER_CONFIGURATION_ATTRIBUTE_KEY));
        return new TigerConfigurationPkiIdentity(substitutedValue);
      } catch (IOException e) {
        throw new TigerConfigurationException(
            "Error while deserializing from JSON: " + e.getMessage(), e);
      }
    }
  }

  public static class TigerPkiIdentitySerializer
      extends JsonSerializer<TigerConfigurationPkiIdentity> {

    @Override
    public void serialize(
        TigerConfigurationPkiIdentity value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.getFileLoadingInformation());
    }
  }
}
