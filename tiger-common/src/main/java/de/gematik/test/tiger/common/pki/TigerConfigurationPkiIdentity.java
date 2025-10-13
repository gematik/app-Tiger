/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.common.pki;

import static de.gematik.test.tiger.common.config.TigerConfigurationLoader.TIGER_CONFIGURATION_ATTRIBUTE_KEY;
import static de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.parseInformationString;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity.TigerPkiIdentityDeserializer;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity.TigerPkiIdentitySerializer;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.StoreType;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.val;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonDeserialize(using = TigerPkiIdentityDeserializer.class)
@JsonSerialize(using = TigerPkiIdentitySerializer.class)
public class TigerConfigurationPkiIdentity extends TigerPkiIdentity {
  private TigerPkiIdentityInformation fileLoadingInformation;

  public TigerConfigurationPkiIdentity(String informationString) {
    super(informationString);
    this.fileLoadingInformation = parseInformationString(informationString);
  }

  public TigerConfigurationPkiIdentity(TigerPkiIdentityInformation fileLoadingInformation) {
    super(fileLoadingInformation);
    this.fileLoadingInformation = fileLoadingInformation;
  }

  public static class TigerPkiIdentityDeserializer
      extends JsonDeserializer<TigerConfigurationPkiIdentity> {

    @Override
    public TigerConfigurationPkiIdentity deserialize(
        JsonParser jsonParser, DeserializationContext ctxt) {
      try {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        if (node.isTextual()) {
          final String substitutedValue = replacePlaceholders(ctxt, node.asText());
          return new TigerConfigurationPkiIdentity(substitutedValue);
        }

        if (node.isObject()) {
          val pkiLoadingInformation = new TigerPkiIdentityInformation();
          pkiLoadingInformation.setFilenames(
              List.of(
                  findField(node, "filename", ctxt)
                      .or(() -> findField(node, "fileName", ctxt))
                      .orElseThrow(
                          () ->
                              new TigerConfigurationException(
                                  "Missing filename in TigerConfigurationPkiIdentity"))));
          findField(node, "password", ctxt).ifPresent(pkiLoadingInformation::setPassword);
          findField(node, "storeType", ctxt)
              .or(() -> findField(node, "storetype", ctxt))
              .flatMap(StoreType::findStoreTypeForString)
              .ifPresent(pkiLoadingInformation::setStoreType);
          findField(node, "alias", ctxt).ifPresent(pkiLoadingInformation::setAlias);
          return new TigerConfigurationPkiIdentity(pkiLoadingInformation);
        }

        throw new IOException("Unsupported YAML structure for TigerConfigurationPkiIdentity");
      } catch (IOException e) {
        throw new TigerConfigurationException(
            "Error while deserializing from JSON: " + e.getMessage(), e);
      }
    }

    private static Optional<String> findField(
        JsonNode node, String fieldname, DeserializationContext ctxt) {
      if (node.hasNonNull(fieldname) && node.get(fieldname).isTextual()) {
        return Optional.of(replacePlaceholders(ctxt, node.get(fieldname).asText()));
      }
      return Optional.empty();
    }

    private static String replacePlaceholders(DeserializationContext ctxt, String text) {
      return TokenSubstituteHelper.substitute(
          text, (TigerConfigurationLoader) ctxt.getAttribute(TIGER_CONFIGURATION_ATTRIBUTE_KEY));
    }
  }

  public static class TigerPkiIdentitySerializer
      extends JsonSerializer<TigerConfigurationPkiIdentity> {

    @Override
    public void serialize(
        TigerConfigurationPkiIdentity value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      if (value.getFileLoadingInformation() != null
          && value.getFileLoadingInformation().isUseCompactFormat()) {
        gen.writeString(value.getFileLoadingInformation().generateCompactFormat());
        return;
      }
      serializers
          .findValueSerializer(TigerPkiIdentityInformation.class)
          .serialize(value.getFileLoadingInformation(), gen, serializers);
    }
  }
}
