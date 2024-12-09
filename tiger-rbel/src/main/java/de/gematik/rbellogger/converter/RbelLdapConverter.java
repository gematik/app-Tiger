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
package de.gematik.rbellogger.converter;

import com.unboundid.asn1.ASN1Element;
import com.unboundid.asn1.ASN1Exception;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.SearchResultEntryProtocolOp;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPException;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo(onlyActivateFor = "ldap")
@Slf4j
public class RbelLdapConverter implements RbelConverterPlugin {

  private final byte[] ldapMessagePrefix = {0x30};

  @Override
  public void consumeElement(final RbelElement rbelElement, final RbelConverter converter) {
    val potentiallyLdap = rbelElement.getContent().startsWith(ldapMessagePrefix);
    if (!potentiallyLdap) {
      return;
    }
    parseLdapMessage(rbelElement, converter);
  }

  private void parseLdapMessage(final RbelElement rbelElement, final RbelConverter converter) {
    try {
      val asn1Element = ASN1Element.decode(rbelElement.getRawContent());
      val ldapMessage = LDAPMessage.decode(asn1Element);

      val textRepresentationElement =
          extractBy(LDAPMessage::toString, ldapMessage, rbelElement, converter);
      val msgIdElement =
          extractBy(m -> Integer.toString(m.getMessageID()), ldapMessage, rbelElement, converter);
      val protocolOpElement =
          extractBy(
              m -> m.getProtocolOp().getClass().getSimpleName(),
              ldapMessage,
              rbelElement,
              converter);

      RbelElement attributes = converter.convertElement("attributes", rbelElement);
      val attributesFacet = extractAttributes(ldapMessage, rbelElement, converter);
      if (!attributesFacet.isEmpty()) {
        attributes.addFacet(attributesFacet);
      }

      val rbelLdapFacet =
          new RbelLdapFacet(textRepresentationElement, msgIdElement, protocolOpElement, attributes);
      rbelElement.addFacet(rbelLdapFacet);

      rbelElement.addFacet(new RbelRootFacet<>(rbelLdapFacet));
      handleRequestResponse(rbelElement, ldapMessage);
    } catch (final ASN1Exception | LDAPException e) {
      // ignore
    }
  }

  private void handleRequestResponse(RbelElement rbelElement, LDAPMessage ldapMessage) {
    val protocolOp = ldapMessage.getProtocolOp().getClass().getSimpleName();
    if (protocolOp.contains("Request")) {
      rbelElement.addFacet(RbelRequestFacet.builder().menuInfoString(protocolOp).build());
    } else {
      rbelElement.addFacet(new RbelResponseFacet(protocolOp));
    }
  }

  private RbelElement extractBy(
      final Function<LDAPMessage, String> extract,
      final LDAPMessage ldapMessage,
      final RbelElement parentElement,
      final RbelConverter converter) {

    val value = extract.apply(ldapMessage);
    return converter.convertElement(value, parentElement);
  }

  private RbelLdapAttributesFacet extractAttributes(
      final LDAPMessage ldapMessage,
      final RbelElement parentElement,
      final RbelConverter converter) {
    val attributesFacet = new RbelLdapAttributesFacet();

    if (ldapMessage.getProtocolOp() instanceof SearchResultEntryProtocolOp) {
      final SearchResultEntryProtocolOp op = ldapMessage.getSearchResultEntryProtocolOp();
      for (final Attribute attr : op.getAttributes()) {
        for (final String value : attr.getValues()) {
          attributesFacet.put(attr.getName(), converter.convertElement(value, parentElement));
        }
      }
    }

    return attributesFacet;
  }
}
