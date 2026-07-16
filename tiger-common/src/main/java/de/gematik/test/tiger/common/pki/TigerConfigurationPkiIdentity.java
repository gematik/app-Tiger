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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@Slf4j
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

  /** No-arg constructor for Spring ConfigurationProperties binding. */
  public TigerConfigurationPkiIdentity() {
    super();
  }

  /**
   * Setter for Spring ConfigurationProperties binding. When Spring binds nested YAML object
   * properties, it sets filename/password/alias/storeType directly on this object via these
   * delegating setters. After binding completes, initializeFromProperties() is called to construct
   * fileLoadingInformation from these fields.
   */
  public void setFilename(String filename) {
    ensureFileLoadingInformation();
    this.fileLoadingInformation.setFilenames(filename != null ? List.of(filename) : List.of());
  }

  public void setPassword(String password) {
    ensureFileLoadingInformation();
    this.fileLoadingInformation.setPassword(password);
  }

  public void setAlias(String alias) {
    ensureFileLoadingInformation();
    this.fileLoadingInformation.setAlias(alias);
  }

  public void setStoreType(String storeTypeStr) {
    ensureFileLoadingInformation();
    if (storeTypeStr != null) {
      StoreType.findStoreTypeForString(storeTypeStr)
          .ifPresentOrElse(
              st -> this.fileLoadingInformation.setStoreType(st),
              () -> {
                throw new IllegalArgumentException("Unknown storeType: " + storeTypeStr);
              });
    }
  }

  private synchronized void ensureFileLoadingInformation() {
    if (this.fileLoadingInformation == null) {
      this.fileLoadingInformation = TigerPkiIdentityInformation.builder().build();
    }
  }

  public static class TigerPkiIdentityDeserializer
      extends ValueDeserializer<TigerConfigurationPkiIdentity> {

    @Override
    public TigerConfigurationPkiIdentity deserialize(
        JsonParser jsonParser, DeserializationContext ctxt) {
      try {
        JsonNode node = ctxt.readTree(jsonParser);

        if (node.isString()) {
          final String substitutedValue = replacePlaceholders(ctxt, node.asString());
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
      if (node.hasNonNull(fieldname) && node.get(fieldname).isString()) {
        return Optional.of(replacePlaceholders(ctxt, node.get(fieldname).asString()));
      }
      return Optional.empty();
    }

    private static String replacePlaceholders(DeserializationContext ctxt, String text) {
      return TokenSubstituteHelper.substitute(
          text, (TigerConfigurationLoader) ctxt.getAttribute(TIGER_CONFIGURATION_ATTRIBUTE_KEY));
    }
  }

  public static class TigerPkiIdentitySerializer
      extends ValueSerializer<TigerConfigurationPkiIdentity> {

    @Override
    public void serialize(
        TigerConfigurationPkiIdentity value, JsonGenerator gen, SerializationContext ctxt)
        throws JacksonException {
      if (value.getFileLoadingInformation() != null
          && value.getFileLoadingInformation().isUseCompactFormat()) {
        gen.writeString(value.getFileLoadingInformation().generateCompactFormat());
        return;
      }
      ctxt.findValueSerializer(TigerPkiIdentityInformation.class)
          .serialize(value.getFileLoadingInformation(), gen, ctxt);
    }
  }
}
