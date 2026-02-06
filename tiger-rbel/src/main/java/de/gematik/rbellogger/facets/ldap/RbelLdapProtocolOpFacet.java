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
package de.gematik.rbellogger.facets.ldap;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import lombok.Builder;
import lombok.Getter;

/**
 * Facet providing structured access to LDAP protocol operation fields. This allows accessing
 * sub-fields of the protocolOp element like dn, filter, scope, etc.
 */
@Getter
@Builder
public class RbelLdapProtocolOpFacet implements RbelFacet {

  // Operation type wrapped as an element
  private final RbelElement operationType;

  // Common fields
  private final RbelElement dn;

  // ModifyDnRequest fields
  private final RbelElement newRdn;
  private final RbelElement deleteOldRdn;
  private final RbelElement newSuperior;

  // SearchRequest fields
  private final RbelElement baseObject;
  private final RbelElement scope;
  private final RbelElement derefAliases;
  private final RbelElement sizeLimit;
  private final RbelElement timeLimit;
  private final RbelElement typesOnly;
  private final RbelElement filter;

  // BindRequest fields
  private final RbelElement version;
  private final RbelElement name;
  private final RbelElement simple;

  // CompareRequest fields
  private final RbelElement attributeDesc;
  private final RbelElement assertionValue;

  // ExtendedRequest/Response fields
  private final RbelElement requestName;
  private final RbelElement responseName;

  // Response fields (ResultResponse)
  private final RbelElement resultCode;
  private final RbelElement matchedDN;
  private final RbelElement diagnosticMessage;

  // BindResponse fields
  private final RbelElement serverSaslCreds;

  // PasswordModifyRequest/Response fields
  private final RbelElement userIdentity;
  private final RbelElement oldPassword;
  private final RbelElement newPassword;
  private final RbelElement genPassword;

  // WhoAmIResponse field
  private final RbelElement authzId;

  // CancelRequest field
  private final RbelElement cancelId;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .withSkipIfNull("operationType", operationType)
        .withSkipIfNull("dn", dn)
        .withSkipIfNull("newRdn", newRdn)
        .withSkipIfNull("deleteOldRdn", deleteOldRdn)
        .withSkipIfNull("newSuperior", newSuperior)
        .withSkipIfNull("baseObject", baseObject)
        .withSkipIfNull("scope", scope)
        .withSkipIfNull("derefAliases", derefAliases)
        .withSkipIfNull("sizeLimit", sizeLimit)
        .withSkipIfNull("timeLimit", timeLimit)
        .withSkipIfNull("typesOnly", typesOnly)
        .withSkipIfNull("filter", filter)
        .withSkipIfNull("version", version)
        .withSkipIfNull("name", name)
        .withSkipIfNull("simple", simple)
        .withSkipIfNull("attributeDesc", attributeDesc)
        .withSkipIfNull("assertionValue", assertionValue)
        .withSkipIfNull("requestName", requestName)
        .withSkipIfNull("responseName", responseName)
        .withSkipIfNull("resultCode", resultCode)
        .withSkipIfNull("matchedDN", matchedDN)
        .withSkipIfNull("diagnosticMessage", diagnosticMessage)
        .withSkipIfNull("serverSaslCreds", serverSaslCreds)
        .withSkipIfNull("userIdentity", userIdentity)
        .withSkipIfNull("oldPassword", oldPassword)
        .withSkipIfNull("newPassword", newPassword)
        .withSkipIfNull("genPassword", genPassword)
        .withSkipIfNull("authzId", authzId)
        .withSkipIfNull("cancelId", cancelId);
  }
}
