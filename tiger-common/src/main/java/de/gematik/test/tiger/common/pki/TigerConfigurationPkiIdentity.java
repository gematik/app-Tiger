/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.pki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(value = {
    "certificate",
    "privateKey",
    "keyId",
    "certificateChain"
})
public class TigerConfigurationPkiIdentity extends TigerPkiIdentity {
    private String fileLoadingInformation;

    public TigerConfigurationPkiIdentity(String fileLoadingInformation) {
        super(fileLoadingInformation);
        this.fileLoadingInformation = fileLoadingInformation;
    }
}
