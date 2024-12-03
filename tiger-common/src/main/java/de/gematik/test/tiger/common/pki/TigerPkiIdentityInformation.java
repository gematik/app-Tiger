/*
 *
 * Copyright 2024 gematik GmbH
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
 */
package de.gematik.test.tiger.common.pki;

import static de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.guessStoreType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.StoreType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TigerPkiIdentityInformation {
  private List<String> filenames;
  private String password;
  private StoreType storeType;
  @JsonIgnore private boolean useCompactFormat = false;

  @JsonIgnore
  public StoreType getOrGuessStoreType() {
    if (storeType != null) {
      return storeType;
    } else {
      return guessStoreType(filenames)
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Unable to guess store type for filenames '" + filenames + "'"));
    }
  }

  public String generateCompactFormat() {
    if (filenames == null) {
      return "";
    } else {
      return String.join(";", filenames) + ";" + password + ";" + getOrGuessStoreType();
    }
  }
}
