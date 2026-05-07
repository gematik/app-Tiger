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
package de.gematik.test.tiger.proxy.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtManipulationRequest {

  private String name;
  private String condition;

  // New approach: separate location and field
  private String jwtLocation;  // e.g., "$.header.dpop", "$.body.client_assertion", "$.body.subject_token"
  private String jwtField;     // e.g., "header.typ", "body.iss", "body.sub"

  private String replaceWith;
  private String privateKeyPem;
  private Integer deleteAfterNExecutions;
  private Boolean replaceJwk;  // Replace JWK with public key derived from privateKeyPem

  // DPoP ath update: when manipulating access token, also update ath in DPoP
  private String dpopLocation;      // e.g., "$.header.dpop" - where the DPoP JWT is located
  private String dpopPrivateKeyPem; // Private key to re-sign the DPoP JWT
  private Boolean updateAth;        // If true, recalculate ath from manipulated access token
}
