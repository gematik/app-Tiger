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
package de.gematik.rbellogger.facets.ldap;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.directory.api.ldap.extras.extended.cancel.CancelRequest;
import org.apache.directory.api.ldap.extras.extended.cancel.CancelResponse;
import org.apache.directory.api.ldap.extras.extended.pwdModify.PasswordModifyRequest;
import org.apache.directory.api.ldap.extras.extended.pwdModify.PasswordModifyResponse;
import org.apache.directory.api.ldap.extras.extended.startTls.StartTlsRequest;
import org.apache.directory.api.ldap.extras.extended.startTls.StartTlsResponse;
import org.apache.directory.api.ldap.extras.extended.whoAmI.WhoAmIRequest;
import org.apache.directory.api.ldap.extras.extended.whoAmI.WhoAmIResponse;
import org.apache.directory.api.ldap.model.message.*;
import org.bouncycastle.asn1.ASN1InputStream;
import org.jetbrains.annotations.Nullable;

/** Enum representing the different LDAP operation types. */
public enum LdapOperationType {
  BIND_REQUEST("BindRequest"),
  BIND_RESPONSE("BindResponse"),
  UNBIND_REQUEST("UnbindRequest"),
  SEARCH_REQUEST("SearchRequest"),
  SEARCH_RESULT_ENTRY("SearchResultEntry"),
  SEARCH_RESULT_DONE("SearchResultDone"),
  SEARCH_RESULT_REFERENCE("SearchResultReference"),
  MODIFY_REQUEST("ModifyRequest"),
  MODIFY_RESPONSE("ModifyResponse"),
  ADD_REQUEST("AddRequest"),
  ADD_RESPONSE("AddResponse"),
  DELETE_REQUEST("DeleteRequest"),
  DELETE_RESPONSE("DeleteResponse"),
  MODIFY_DN_REQUEST("ModifyDnRequest"),
  MODIFY_DN_RESPONSE("ModifyDnResponse"),
  COMPARE_REQUEST("CompareRequest"),
  COMPARE_RESPONSE("CompareResponse"),
  ABANDON_REQUEST("AbandonRequest"),
  EXTENDED_REQUEST("ExtendedRequest"),
  EXTENDED_RESPONSE("ExtendedResponse"),
  INTERMEDIATE_RESPONSE("IntermediateResponse"),
  START_TLS_REQUEST("StartTlsRequest", "1.3.6.1.4.1.1466.20037", 0x79),
  START_TLS_RESPONSE("StartTlsResponse", "1.3.6.1.4.1.1466.20037", 0x78),
  PASSWORD_MODIFY_REQUEST("PasswordModifyRequest", "1.3.6.1.4.1.4203.1.11.1", 0x79),
  PASSWORD_MODIFY_RESPONSE("PasswordModifyResponse", "1.3.6.1.4.1.4203.1.11.1", 0x78),
  CANCEL_REQUEST("CancelRequest", "1.3.6.1.1.8", 0x79),
  CANCEL_RESPONSE("CancelResponse", "1.3.6.1.1.8", 0x78),
  WHO_AM_I_REQUEST("WhoAmIRequest", "1.3.6.1.4.1.4203.1.11.3", 0x79),
  WHO_AM_I_RESPONSE("WhoAmIResponse", "1.3.6.1.4.1.4203.1.11.3", 0x78),
  UNKNOWN("Unknown", -1);

  private final String rbelName;
  private final int[] opCodes;
  private final String oid;

  LdapOperationType(String rbelName, int... opCodes) {
    this(rbelName, null, opCodes);
  }

  LdapOperationType(String rbelName, String oid, int... opCodes) {
    this.rbelName = rbelName;
    this.opCodes = opCodes;
    this.oid = oid;
  }

  public static Optional<LdapOperationType> fromOpcode(int opCode, @Nullable String oid) {
    if (oid != null && !oid.isBlank()) {
      return Stream.of(
              WHO_AM_I_REQUEST,
              WHO_AM_I_RESPONSE,
              PASSWORD_MODIFY_REQUEST,
              PASSWORD_MODIFY_RESPONSE,
              CANCEL_REQUEST,
              CANCEL_RESPONSE,
              START_TLS_REQUEST,
              START_TLS_RESPONSE)
          .filter(type -> type.oid != null && type.oid.equals(oid))
          .filter(type -> Arrays.stream(type.opCodes).anyMatch(code -> code == opCode))
          .findFirst();
    }

    return Arrays.stream(values())
        .filter(type -> type.oid == null) // only match non-extended ops
        .filter(type -> Arrays.stream(type.opCodes).anyMatch(code -> code == opCode))
        .findFirst()
        .or(() -> Optional.of(UNKNOWN));
  }

  /**
   * Map the LDAP protocolOp tag byte (opcode) to an operation type.
   *
   * <p>For extended operations (0x79 / 0x78) this returns {@link #EXTENDED_REQUEST} or {@link
   * #EXTENDED_RESPONSE}. The concrete extended type must be inferred via OID.
   */
  public static LdapOperationType fromOpCode(int opCode) {
    return switch (opCode) {
      case 0x60 -> BIND_REQUEST;
      case 0x61 -> BIND_RESPONSE;
      case 0x42 -> UNBIND_REQUEST;
      case 0x63 -> SEARCH_REQUEST;
      case 0x64 -> SEARCH_RESULT_ENTRY;
      case 0x65 -> SEARCH_RESULT_DONE;
      case 0x73 -> SEARCH_RESULT_REFERENCE;
      case 0x66 -> MODIFY_REQUEST;
      case 0x67 -> MODIFY_RESPONSE;
      case 0x68 -> ADD_REQUEST;
      case 0x69 -> ADD_RESPONSE;
      case 0x4a -> DELETE_REQUEST;
      case 0x6b -> DELETE_RESPONSE;
      case 0x6c -> MODIFY_DN_REQUEST;
      case 0x6d -> MODIFY_DN_RESPONSE;
      case 0x6e -> COMPARE_REQUEST;
      case 0x6f -> COMPARE_RESPONSE;
      case 0x50 -> ABANDON_REQUEST;
      case 0x77 -> INTERMEDIATE_RESPONSE;
      case 0x79 -> EXTENDED_REQUEST;
      case 0x78 -> EXTENDED_RESPONSE;
      default -> UNKNOWN;
    };
  }

  /**
   * Infer the {@link LdapOperationType} from raw ASN.1 encoded protocolOp bytes.
   *
   * <p>Algorithm:
   *
   * <ol>
   *   <li>Infer by opcode byte.
   *   <li>If opcode denotes extended request/response, try to resolve by OID.
   *   <li>If that fails, return EXTENDED_* (caller may optionally fall back to message-instance
   *       checks).
   * </ol>
   */
  public static LdapOperationType inferFromProtocolOpBytes(
      Message message, byte[] protocolOpBytes) {
    if (protocolOpBytes == null || protocolOpBytes.length == 0) {
      return UNKNOWN;
    }
    int opCode = Byte.toUnsignedInt(protocolOpBytes[0]);
    LdapOperationType byOpCode = fromOpCode(opCode);
    if (byOpCode != EXTENDED_REQUEST
        && byOpCode != EXTENDED_RESPONSE
        && byOpCode != INTERMEDIATE_RESPONSE
        && byOpCode != UNKNOWN) {
      return byOpCode;
    }

    String oid = extractOidFromExtendedOp(protocolOpBytes);
    if (oid != null && !oid.isBlank()) {
      var inferredType = fromOpcode(opCode, oid);
      if (inferredType
          .filter(type -> type != UNKNOWN && type != INTERMEDIATE_RESPONSE)
          .isPresent()) {
        return inferredType.get();
      }
    }
    return inferFromMessage(message);
  }

  /**
   * Fallback inference by Apache Directory API message runtime type. This is mainly used to resolve
   * concrete extended request/response implementations when opcode/OID detection isn't possible.
   */
  public static LdapOperationType inferFromMessage(Message ldapMessage) {
    if (ldapMessage instanceof WhoAmIResponse) {
      return WHO_AM_I_RESPONSE;
    } else if (ldapMessage instanceof WhoAmIRequest) {
      return WHO_AM_I_REQUEST;
    } else if (ldapMessage instanceof PasswordModifyResponse) {
      return PASSWORD_MODIFY_RESPONSE;
    } else if (ldapMessage instanceof PasswordModifyRequest) {
      return PASSWORD_MODIFY_REQUEST;
    } else if (ldapMessage instanceof CancelRequest) {
      return CANCEL_REQUEST;
    } else if (ldapMessage instanceof CancelResponse) {
      return CANCEL_RESPONSE;
    } else if (ldapMessage instanceof StartTlsRequest) {
      return START_TLS_REQUEST;
    } else if (ldapMessage instanceof StartTlsResponse) {
      return START_TLS_RESPONSE;
    } else if (ldapMessage instanceof BindRequest) {
      return BIND_REQUEST;
    } else if (ldapMessage instanceof BindResponse) {
      return BIND_RESPONSE;
    } else if (ldapMessage instanceof UnbindRequest) {
      return UNBIND_REQUEST;
    } else if (ldapMessage instanceof SearchRequest) {
      return SEARCH_REQUEST;
    } else if (ldapMessage instanceof SearchResultEntry) {
      return SEARCH_RESULT_ENTRY;
    } else if (ldapMessage instanceof SearchResultDone) {
      return SEARCH_RESULT_DONE;
    } else if (ldapMessage instanceof SearchResultReference) {
      return SEARCH_RESULT_REFERENCE;
    } else if (ldapMessage instanceof ModifyRequest) {
      return MODIFY_REQUEST;
    } else if (ldapMessage instanceof ModifyResponse) {
      return MODIFY_RESPONSE;
    } else if (ldapMessage instanceof AddRequest) {
      return ADD_REQUEST;
    } else if (ldapMessage instanceof AddResponse) {
      return ADD_RESPONSE;
    } else if (ldapMessage instanceof DeleteRequest) {
      return DELETE_REQUEST;
    } else if (ldapMessage instanceof DeleteResponse) {
      return DELETE_RESPONSE;
    } else if (ldapMessage instanceof ModifyDnRequest) {
      return MODIFY_DN_REQUEST;
    } else if (ldapMessage instanceof ModifyDnResponse) {
      return MODIFY_DN_RESPONSE;
    } else if (ldapMessage instanceof CompareRequest) {
      return COMPARE_REQUEST;
    } else if (ldapMessage instanceof CompareResponse) {
      return COMPARE_RESPONSE;
    } else if (ldapMessage instanceof AbandonRequest) {
      return ABANDON_REQUEST;
    } else if (ldapMessage instanceof ExtendedRequest) {
      return EXTENDED_REQUEST;
    } else if (ldapMessage instanceof ExtendedResponse) {
      return EXTENDED_RESPONSE;
    } else if (ldapMessage instanceof IntermediateResponse) {
      return INTERMEDIATE_RESPONSE;
    }
    return UNKNOWN;
  }

  /** Recursively search for tag 10 (OID) in ASN.1 structure. */
  private static String findOidInAsn1(Object obj) {
    if (obj instanceof org.bouncycastle.asn1.ASN1TaggedObject tagged) {
      if (tagged.getTagNo() == 10
          && tagged.getBaseObject() instanceof org.bouncycastle.asn1.ASN1OctetString octet) {
        return new String(octet.getOctets());
      }
      return findOidInAsn1(tagged.getBaseObject());
    }
    if (obj instanceof org.bouncycastle.asn1.ASN1Sequence seq) {
      for (int i = 0; i < seq.size(); i++) {
        String found = findOidInAsn1(seq.getObjectAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }

  /** Extracts the OID from ASN.1 encoded ExtendedRequest/ExtendedResponse bytes. */
  private static String extractOidFromExtendedOp(byte[] protocolOpBytes) {
    try (ASN1InputStream asn1InputStream = new ASN1InputStream(protocolOpBytes)) {
      var obj = asn1InputStream.readObject();
      String found = findOidInAsn1(obj);
      if (found != null) return found;
    } catch (Exception e) {
      // ignore, fallback to null
    }
    return null;
  }

  @Override
  public String toString() {
    return rbelName;
  }

  public String getExtendedDisplayName() {
    return switch (this) {
      case START_TLS_REQUEST, START_TLS_RESPONSE -> "1.3.6.1.4.1.1466.20037 (StartTLS)";
      case PASSWORD_MODIFY_REQUEST, PASSWORD_MODIFY_RESPONSE ->
          "1.3.6.1.4.1.4203.1.11.1 (PasswordModify)";
      case WHO_AM_I_REQUEST, WHO_AM_I_RESPONSE -> "1.3.6.1.4.1.4203.1.11.3 (WhoAmI)";
      case CANCEL_REQUEST, CANCEL_RESPONSE -> "1.3.6.1.1.8 (Cancel)";
      default -> null;
    };
  }
}
