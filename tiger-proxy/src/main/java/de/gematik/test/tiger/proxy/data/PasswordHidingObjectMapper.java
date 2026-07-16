/*
 * Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.proxy.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRouteAuthenticationConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityInformation;
import java.util.List;
import lombok.NoArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates an ObjectMapper that:
 *
 * <ul>
 *   <li>Suppresses fields equal to their default values (via NON_DEFAULT mixins overriding
 *       class-level NON_NULL annotations)
 *   <li>Hides password and bearerToken fields via @JsonIgnore mixins
 * </ul>
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class PasswordHidingObjectMapper {

  public static ObjectMapper createObjectMapper() {
    return JsonMapper.builder()
        .addMixIn(TigerProxyConfiguration.class, TigerProxyConfigurationMixIn.class)
        .addMixIn(TigerTlsConfiguration.class, TigerTlsConfigurationMixIn.class)
        .addMixIn(ForwardProxyInfo.class, ForwardProxyInfoMixIn.class)
        .addMixIn(TigerPkiIdentityInformation.class, TigerPkiIdentityInformationMixIn.class)
        .addMixIn(TigerConfigurationPkiIdentity.class, TigerConfigurationPkiIdentityMixIn.class)
        .addMixIn(
            TigerRouteAuthenticationConfiguration.class,
            TigerRouteAuthenticationConfigurationMixIn.class)
        .build();
  }
}

/**
 * Mixins to customize serialization of TigerProxyConfiguration. NON_DEFAULT on the mixin overrides
 * the target class's own @JsonInclude(NON_NULL), so only values that differ from the no-arg
 * constructor defaults are serialized. Password / secret fields are hidden via @JsonIgnore.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
interface TigerProxyConfigurationMixIn {}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
interface TigerTlsConfigurationMixIn {}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract class ForwardProxyInfoMixIn {
  @JsonIgnore String password;
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract class TigerPkiIdentityInformationMixIn {
  @JsonIgnore String password;
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonSerialize(using = TigerPkiIdentitySerializerPasswordHiding.class)
abstract class TigerConfigurationPkiIdentityMixIn {
  @JsonIgnore String password;
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract class TigerRouteAuthenticationConfigurationMixIn {
  @JsonIgnore String password;
  @JsonIgnore String bearerToken;
}

class TigerPkiIdentitySerializerPasswordHiding
    extends ValueSerializer<TigerConfigurationPkiIdentity> {

  @Override
  public void serialize(
      TigerConfigurationPkiIdentity value, JsonGenerator gen, SerializationContext ctxt)
      throws JacksonException {
    final var info = value.getFileLoadingInformation();
    if (info == null) {
      ctxt.findValueSerializer(TigerPkiIdentityInformation.class).serialize(null, gen, ctxt);
      return;
    }
    // Create a copy with the password removed to avoid mutating the original object
    final var safeInfo = info.toBuilder().password(null).aliasesOrPasswords(List.of()).build();
    if (safeInfo.isUseCompactFormat()) {
      gen.writeString(safeInfo.generateCompactFormat());
      return;
    }
    ctxt.findValueSerializer(TigerPkiIdentityInformation.class).serialize(safeInfo, gen, ctxt);
  }
}
