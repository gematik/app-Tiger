/*
 *
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.ldap.LdapOperationType;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.apache.directory.api.asn1.util.Asn1Buffer;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.codec.api.LdapApiServiceFactory;
import org.apache.directory.api.ldap.codec.api.LdapEncoder;
import org.apache.directory.api.ldap.extras.extended.startTls.StartTlsRequestImpl;
import org.apache.directory.api.ldap.extras.extended.startTls.StartTlsResponseImpl;
import org.apache.directory.api.ldap.extras.extended.whoAmI.WhoAmIRequestImpl;
import org.apache.directory.api.ldap.model.message.BindRequestImpl;
import org.apache.directory.api.ldap.model.message.BindResponseImpl;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.bouncycastle.asn1.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for LDAP multi-response pairing behaviour in {@link
 * de.gematik.rbellogger.facets.ldap.RbelLdapPairingConverter} and the {@link LdapOperationType}
 * helpers {@code isMultiResponseRequest()} and {@code isTerminalResponse()}.
 */
class RbelLdapPairingConverterTest {

  private static final LdapApiService codec = LdapApiServiceFactory.getSingleton();
  private final RbelConfiguration config = new RbelConfiguration().activateConversionFor("ldap");
  private final RbelConverter rbelConverter = RbelLogger.build(config).getRbelConverter();

  // -----------------------------------------------------------------------
  // LdapOperationType classification helpers
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("LdapOperationType.isMultiResponseRequest()")
  class IsMultiResponseRequest {

    @ParameterizedTest
    @EnumSource(
        value = LdapOperationType.class,
        names = {
          "SEARCH_REQUEST",
          "EXTENDED_REQUEST",
          "START_TLS_REQUEST",
          "PASSWORD_MODIFY_REQUEST",
          "CANCEL_REQUEST",
          "WHO_AM_I_REQUEST"
        })
    @DisplayName("should return true for multi-response request types")
    void multiResponseRequests(LdapOperationType type) {
      assertThat(type.isMultiResponseRequest())
          .describedAs("%s should be multi-response", type)
          .isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = LdapOperationType.class,
        names = {
          "BIND_REQUEST",
          "UNBIND_REQUEST",
          "MODIFY_REQUEST",
          "ADD_REQUEST",
          "DELETE_REQUEST",
          "MODIFY_DN_REQUEST",
          "COMPARE_REQUEST",
          "ABANDON_REQUEST"
        })
    @DisplayName("should return false for single-response request types")
    void singleResponseRequests(LdapOperationType type) {
      assertThat(type.isMultiResponseRequest())
          .describedAs("%s should NOT be multi-response", type)
          .isFalse();
    }

    @ParameterizedTest
    @EnumSource(
        value = LdapOperationType.class,
        names = {
          "BIND_RESPONSE",
          "SEARCH_RESULT_ENTRY",
          "SEARCH_RESULT_DONE",
          "SEARCH_RESULT_REFERENCE",
          "MODIFY_RESPONSE",
          "ADD_RESPONSE",
          "DELETE_RESPONSE",
          "MODIFY_DN_RESPONSE",
          "COMPARE_RESPONSE",
          "EXTENDED_RESPONSE",
          "INTERMEDIATE_RESPONSE",
          "START_TLS_RESPONSE",
          "PASSWORD_MODIFY_RESPONSE",
          "CANCEL_RESPONSE",
          "WHO_AM_I_RESPONSE",
          "UNKNOWN"
        })
    @DisplayName("should return false for response and unknown types")
    void responseTypes(LdapOperationType type) {
      assertThat(type.isMultiResponseRequest())
          .describedAs("%s (response) should NOT be multi-response", type)
          .isFalse();
    }
  }

  @Nested
  @DisplayName("LdapOperationType.isTerminalResponse()")
  class IsTerminalResponse {

    @ParameterizedTest
    @EnumSource(
        value = LdapOperationType.class,
        names = {
          "SEARCH_RESULT_DONE",
          "EXTENDED_RESPONSE",
          "START_TLS_RESPONSE",
          "PASSWORD_MODIFY_RESPONSE",
          "CANCEL_RESPONSE",
          "WHO_AM_I_RESPONSE"
        })
    @DisplayName("should return true for terminal response types")
    void terminalResponses(LdapOperationType type) {
      assertThat(type.isTerminalResponse()).describedAs("%s should be terminal", type).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = LdapOperationType.class,
        names = {
          "SEARCH_RESULT_ENTRY",
          "SEARCH_RESULT_REFERENCE",
          "INTERMEDIATE_RESPONSE",
          "BIND_RESPONSE",
          "MODIFY_RESPONSE",
          "ADD_RESPONSE",
          "DELETE_RESPONSE",
          "MODIFY_DN_RESPONSE",
          "COMPARE_RESPONSE"
        })
    @DisplayName("should return false for non-terminal response types")
    void nonTerminalResponses(LdapOperationType type) {
      assertThat(type.isTerminalResponse())
          .describedAs("%s should NOT be terminal", type)
          .isFalse();
    }

    @ParameterizedTest
    @EnumSource(
        value = LdapOperationType.class,
        names = {
          "SEARCH_REQUEST",
          "BIND_REQUEST",
          "UNBIND_REQUEST",
          "MODIFY_REQUEST",
          "ADD_REQUEST",
          "DELETE_REQUEST",
          "MODIFY_DN_REQUEST",
          "COMPARE_REQUEST",
          "ABANDON_REQUEST",
          "EXTENDED_REQUEST",
          "START_TLS_REQUEST",
          "PASSWORD_MODIFY_REQUEST",
          "CANCEL_REQUEST",
          "WHO_AM_I_REQUEST",
          "UNKNOWN"
        })
    @DisplayName("should return false for request types")
    void requestTypes(LdapOperationType type) {
      assertThat(type.isTerminalResponse())
          .describedAs("%s (request) should NOT be terminal", type)
          .isFalse();
    }
  }

  // -----------------------------------------------------------------------
  // Pairing converter integration tests
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("Search request pairing")
  class SearchRequestPairing {

    @SneakyThrows
    @Test
    @DisplayName("SEARCH_REQUEST should have responseRequired=true")
    void searchRequestHasResponseRequired() {
      var searchReq = new SearchRequestImpl();
      searchReq.setMessageId(1);
      searchReq.setBase(new Dn("dc=example,dc=com"));
      searchReq.setScope(SearchScope.SUBTREE);
      searchReq.setFilter("(objectClass=*)");

      RbelElement elem = parseMessage(searchReq);

      assertThat(elem.hasFacet(RbelRequestFacet.class)).isTrue();
      assertThat(elem.getFacetOrFail(RbelRequestFacet.class).isResponseRequired()).isTrue();
    }

    @SneakyThrows
    @Test
    @DisplayName("SEARCH_REQUEST paired with entries + DONE should be complete with all responses")
    void searchRequestPairsWithAllEntries() {
      var searchReq = new SearchRequestImpl();
      searchReq.setMessageId(1);
      searchReq.setBase(new Dn("dc=example,dc=com"));
      searchReq.setScope(SearchScope.SUBTREE);
      searchReq.setFilter("(objectClass=*)");

      RbelElement reqElem = parseAndAddToHistory(searchReq);

      RbelElement entry1 = parseAndAddToHistory(buildSearchResultEntryBytes(1, "cn=Alice"));
      RbelElement entry2 = parseAndAddToHistory(buildSearchResultEntryBytes(1, "cn=Bob"));

      var pairBeforeDone = reqElem.getFacet(TracingMessagePairFacet.class);
      assertThat(pairBeforeDone).isPresent();
      assertThat(pairBeforeDone.get().getResponses()).hasSize(2);
      assertThat(pairBeforeDone.get().isResponseComplete()).isFalse();

      RbelElement done = parseAndAddToHistory(buildSearchResultDoneBytes(1));

      var pairAfterDone = reqElem.getFacet(TracingMessagePairFacet.class);
      assertThat(pairAfterDone).isPresent();
      assertThat(pairAfterDone.get().getResponses()).hasSize(3);
      assertThat(pairAfterDone.get().isResponseComplete()).isTrue();

      assertThat(entry1.getFacet(TracingMessagePairFacet.class).orElseThrow())
          .isSameAs(pairAfterDone.get());
      assertThat(entry2.getFacet(TracingMessagePairFacet.class).orElseThrow())
          .isSameAs(pairAfterDone.get());
      assertThat(done.getFacet(TracingMessagePairFacet.class).orElseThrow())
          .isSameAs(pairAfterDone.get());
    }
  }

  @Nested
  @DisplayName("Extended request pairing")
  class ExtendedRequestPairing {

    @SneakyThrows
    @Test
    @DisplayName("StartTLS request should have responseRequired=true")
    void startTlsRequestHasResponseRequired() {
      var startTlsReq = new StartTlsRequestImpl();
      startTlsReq.setMessageId(10);

      RbelElement elem = parseMessage(startTlsReq);

      assertThat(elem.hasFacet(RbelRequestFacet.class)).isTrue();
      assertThat(elem.getFacetOrFail(RbelRequestFacet.class).isResponseRequired()).isTrue();
    }

    @SneakyThrows
    @Test
    @DisplayName("WhoAmI request should have responseRequired=true")
    void whoAmIRequestHasResponseRequired() {
      var whoAmIReq = new WhoAmIRequestImpl();
      whoAmIReq.setMessageId(11);

      RbelElement elem = parseMessage(whoAmIReq);

      assertThat(elem.hasFacet(RbelRequestFacet.class)).isTrue();
      assertThat(elem.getFacetOrFail(RbelRequestFacet.class).isResponseRequired()).isTrue();
    }

    @SneakyThrows
    @Test
    @DisplayName("WhoAmI request paired with only the final response should be complete")
    void extendedRequestPairsWithFinalResponse() {
      var whoAmIReq = new WhoAmIRequestImpl();
      whoAmIReq.setMessageId(20);

      RbelElement reqElem = parseAndAddToHistory(whoAmIReq);

      byte[] authzId = "dn:uid=test,dc=example,dc=com".getBytes();
      RbelElement finalResp =
          parseAndAddToHistory(encodeExtendedResponseBytes(20, "1.3.6.1.4.1.4203.1.11.3", authzId));

      var pair = reqElem.getFacet(TracingMessagePairFacet.class);
      assertThat(pair).isPresent();
      assertThat(pair.get().getResponses()).hasSize(1);
      assertThat(pair.get().isResponseComplete()).isTrue();

      assertThat(finalResp.getFacet(TracingMessagePairFacet.class).orElseThrow())
          .isSameAs(pair.get());
    }

    @SneakyThrows
    @Test
    @DisplayName("StartTLS request + response should be paired and complete")
    void startTlsRequestResponsePaired() {
      var startTlsReq = new StartTlsRequestImpl();
      startTlsReq.setMessageId(30);
      RbelElement reqElem = parseAndAddToHistory(startTlsReq);

      var startTlsResp = new StartTlsResponseImpl(30);
      RbelElement respElem = parseAndAddToHistory(startTlsResp);

      var pair = reqElem.getFacet(TracingMessagePairFacet.class);
      assertThat(pair).isPresent();
      assertThat(pair.get().getResponses()).hasSize(1);
      assertThat(pair.get().isResponseComplete()).isTrue();
      assertThat(pair.get().getRequest()).isSameAs(reqElem);
      assertThat(respElem.getFacet(TracingMessagePairFacet.class).orElseThrow())
          .isSameAs(pair.get());
    }
  }

  @Nested
  @DisplayName("Single-response request pairing")
  class SingleResponseRequestPairing {

    @SneakyThrows
    @Test
    @DisplayName("BIND_REQUEST should have responseRequired=false")
    void bindRequestHasNoResponseRequired() {
      var bindReq = new BindRequestImpl();
      bindReq.setMessageId(50);
      bindReq.setVersion3(true);
      bindReq.setSimple(true);

      RbelElement elem = parseMessage(bindReq);

      assertThat(elem.hasFacet(RbelRequestFacet.class)).isTrue();
      assertThat(elem.getFacetOrFail(RbelRequestFacet.class).isResponseRequired()).isFalse();
    }

    @SneakyThrows
    @Test
    @DisplayName("BIND_REQUEST + BIND_RESPONSE should be paired and immediately complete")
    void bindRequestResponsePaired() {
      var bindReq = new BindRequestImpl();
      bindReq.setMessageId(50);
      bindReq.setVersion3(true);
      bindReq.setSimple(true);
      RbelElement reqElem = parseAndAddToHistory(bindReq);

      var bindResp = new BindResponseImpl(50);
      RbelElement respElem = parseAndAddToHistory(bindResp);

      var pair = reqElem.getFacet(TracingMessagePairFacet.class);
      assertThat(pair).isPresent();
      assertThat(pair.get().getResponses()).hasSize(1);
      assertThat(pair.get().isResponseComplete()).isTrue();
      assertThat(respElem.getFacet(TracingMessagePairFacet.class).orElseThrow())
          .isSameAs(pair.get());
    }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  @SneakyThrows
  private RbelElement parseMessage(org.apache.directory.api.ldap.model.message.Message msg) {
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, msg);
    return rbelConverter.convertElement(buffer.getBytes().array(), null);
  }

  @SneakyThrows
  private RbelElement parseAndAddToHistory(
      org.apache.directory.api.ldap.model.message.Message msg) {
    Asn1Buffer buffer = new Asn1Buffer();
    LdapEncoder.encodeMessage(buffer, codec, msg);
    return parseAndAddToHistory(buffer.getBytes().array());
  }

  private RbelElement parseAndAddToHistory(byte[] bytes) {
    RbelElement elem = new RbelElement(bytes, null);
    rbelConverter.parseMessage(elem, new RbelMessageMetadata());
    return elem;
  }

  /**
   * Builds a minimal SearchResultEntry (opcode 0x64) with the given messageId and DN. This is a
   * simplified encoding: just messageId + protocolOp with DN and empty attributes.
   */
  @SneakyThrows
  private byte[] buildSearchResultEntryBytes(int messageId, String dn) {
    var components = new ASN1EncodableVector();
    components.add(new DEROctetString(dn.getBytes(StandardCharsets.UTF_8)));
    components.add(new DERSequence()); // empty attributes
    DERSequence entrySeq = new DERSequence(components);
    byte[] entryDer = entrySeq.getEncoded(ASN1Encoding.DER);
    entryDer[0] = 0x64; // SEARCH_RESULT_ENTRY tag
    var entryPrimitive = ASN1Primitive.fromByteArray(entryDer);

    var msgComponents = new ASN1EncodableVector();
    msgComponents.add(new ASN1Integer(messageId));
    msgComponents.add(entryPrimitive);
    return new DERSequence(msgComponents).getEncoded(ASN1Encoding.DER);
  }

  /**
   * Builds a minimal SearchResultDone (opcode 0x65) with the given messageId. Contains SUCCESS
   * result code, empty matchedDN and diagnosticMessage.
   */
  @SneakyThrows
  private byte[] buildSearchResultDoneBytes(int messageId) {
    var components = new ASN1EncodableVector();
    components.add(new ASN1Enumerated(0)); // SUCCESS
    components.add(new DEROctetString(new byte[0])); // matchedDN
    components.add(new DEROctetString(new byte[0])); // diagnosticMessage
    DERSequence doneSeq = new DERSequence(components);
    byte[] doneDer = doneSeq.getEncoded(ASN1Encoding.DER);
    doneDer[0] = 0x65; // SEARCH_RESULT_DONE tag
    var donePrimitive = ASN1Primitive.fromByteArray(doneDer);

    var msgComponents = new ASN1EncodableVector();
    msgComponents.add(new ASN1Integer(messageId));
    msgComponents.add(donePrimitive);
    return new DERSequence(msgComponents).getEncoded(ASN1Encoding.DER);
  }

  /** Builds a complete ExtendedResponse with the given messageId, OID, and optional value. */
  @SneakyThrows
  private byte[] encodeExtendedResponseBytes(int messageId, String responseName, byte[] value) {
    var components = new ASN1EncodableVector();
    components.add(new ASN1Enumerated(0)); // SUCCESS
    components.add(new DEROctetString(new byte[0])); // matchedDN
    components.add(new DEROctetString(new byte[0])); // diagnosticMessage
    if (responseName != null) {
      components.add(
          new DERTaggedObject(
              false, 10, new DEROctetString(responseName.getBytes(StandardCharsets.US_ASCII))));
    }
    if (value != null) {
      components.add(new DERTaggedObject(false, 11, new DEROctetString(value)));
    }
    DERSequence respSeq = new DERSequence(components);
    byte[] respDer = respSeq.getEncoded(ASN1Encoding.DER);
    respDer[0] = 0x78; // EXTENDED_RESPONSE tag
    var respPrimitive = ASN1Primitive.fromByteArray(respDer);

    var msgComponents = new ASN1EncodableVector();
    msgComponents.add(new ASN1Integer(messageId));
    msgComponents.add(respPrimitive);
    return new DERSequence(msgComponents).getEncoded(ASN1Encoding.DER);
  }
}
