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
import lombok.Value;

/**
 * Facet providing structured access to LDAP protocol operation fields. This allows accessing
 * sub-fields of the protocolOp element like dn, filter, scope, etc.
 */
@Value
@Builder
public class RbelLdapProtocolOpFacet implements RbelFacet {

  // Operation type wrapped as an element
  RbelElement operationType;

  // Common fields
  RbelElement dn;

  // ModifyDnRequest fields
  RbelElement newRdn;
  RbelElement deleteOldRdn;
  RbelElement newSuperior;

  // SearchRequest fields
  RbelElement baseObject;
  RbelElement scope;
  RbelElement derefAliases;
  RbelElement sizeLimit;
  RbelElement timeLimit;
  RbelElement typesOnly;
  RbelElement filter;
  RbelElement requestedAttributes;

  // BindRequest fields
  RbelElement version;
  RbelElement name;
  RbelElement simple;
  RbelElement saslMechanism;
  RbelElement saslCredentials;

  // CompareRequest fields
  RbelElement attributeDesc;
  RbelElement assertionValue;

  // ExtendedRequest/Response fields
  RbelElement requestName;
  RbelElement requestValue;
  RbelElement responseName;
  RbelElement responseValue;

  // Response fields (ResultResponse)
  RbelElement resultCode;
  RbelElement matchedDN;
  RbelElement diagnosticMessage;
  RbelElement referrals;

  // BindResponse fields
  RbelElement serverSaslCreds;

  // PasswordModifyRequest/Response fields
  RbelElement userIdentity;
  RbelElement oldPassword;
  RbelElement newPassword;
  RbelElement genPassword;

  // WhoAmIResponse field
  RbelElement authzId;

  // CancelRequest field
  RbelElement cancelId;

  // AbandonRequest field
  RbelElement abandonedMessageId;

  @Getter(lazy = true)
  RbelMultiMap<RbelElement> childElements =
      new RbelMultiMap<RbelElement>()
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
          .withSkipIfNull("requestedAttributes", requestedAttributes)
          .withSkipIfNull("version", version)
          .withSkipIfNull("name", name)
          .withSkipIfNull("simple", simple)
          .withSkipIfNull("saslMechanism", saslMechanism)
          .withSkipIfNull("saslCredentials", saslCredentials)
          .withSkipIfNull("attributeDesc", attributeDesc)
          .withSkipIfNull("assertionValue", assertionValue)
          .withSkipIfNull("requestName", requestName)
          .withSkipIfNull("requestValue", requestValue)
          .withSkipIfNull("responseName", responseName)
          .withSkipIfNull("responseValue", responseValue)
          .withSkipIfNull("resultCode", resultCode)
          .withSkipIfNull("matchedDN", matchedDN)
          .withSkipIfNull("diagnosticMessage", diagnosticMessage)
          .withSkipIfNull("referrals", referrals)
          .withSkipIfNull("serverSaslCreds", serverSaslCreds)
          .withSkipIfNull("userIdentity", userIdentity)
          .withSkipIfNull("oldPassword", oldPassword)
          .withSkipIfNull("newPassword", newPassword)
          .withSkipIfNull("genPassword", genPassword)
          .withSkipIfNull("authzId", authzId)
          .withSkipIfNull("cancelId", cancelId)
          .withSkipIfNull("abandonedMessageId", abandonedMessageId);
}
