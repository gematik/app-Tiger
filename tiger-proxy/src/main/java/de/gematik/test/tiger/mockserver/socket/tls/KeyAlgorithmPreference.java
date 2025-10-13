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
package de.gematik.test.tiger.mockserver.socket.tls;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Hashtable;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.jcajce.provider.asymmetric.RSA;
import org.bouncycastle.tls.*;

@Slf4j
public enum KeyAlgorithmPreference {
  UNKNOWN,
  MIXED,
  RSA,
  ECC;

  /**
   * Determines the client's preferred key algorithm (RSA or ECC) for server certificates based on
   * the ClientHello message. Prioritizes the signature_algorithms extension, then falls back to
   * cipher suites.
   *
   * @param clientHelloByteBuf The raw bytes of the ClientHello message.
   * @return The determined KeyAlgorithmPreference.
   */
  public static KeyAlgorithmPreference determineKeyAlgorithmPreference(ByteBuf clientHelloByteBuf) {
    try {
      val bb = new byte[clientHelloByteBuf.readableBytes()];
      clientHelloByteBuf.getBytes(0, bb);
      return determineKeyAlgorithmPreference(bb);
    } catch (RuntimeException e) {
      log.error("Error determining key algorithm preference: " + e.getMessage(), e);
      return de.gematik.test.tiger.mockserver.socket.tls.KeyAlgorithmPreference.UNKNOWN;
    } finally {
      clientHelloByteBuf.resetReaderIndex();
    }
  }

  private static KeyAlgorithmPreference determineKeyAlgorithmPreference(byte[] clientHelloBytes) {
    try {
      ByteArrayInputStream bis = new ByteArrayInputStream(clientHelloBytes);
      ClientHello clientHello = ClientHello.parse(bis, null);

      KeyAlgorithmPreference fromSigAlgs =
          getKeyAlgorithmFromSignatureAlgorithms(clientHello.getExtensions());
      KeyAlgorithmPreference fromCipherSuites =
          getKeyAlgorithmFromCipherSuites(clientHello.getCipherSuites());

      return KeyAlgorithmPreference.determineEffectivePreference(fromSigAlgs, fromCipherSuites);
    } catch (IOException e) {
      log.warn("Error parsing ClientHello: " + e.getMessage(), e);
      return de.gematik.test.tiger.mockserver.socket.tls.KeyAlgorithmPreference.UNKNOWN;
    }
  }

  private static KeyAlgorithmPreference getKeyAlgorithmFromSignatureAlgorithms(
      Hashtable<Integer, byte[]> extensions) throws IOException {
    if (extensions != null) {
      byte[] signatureAlgorithmsExtension = extensions.get(ExtensionType.signature_algorithms);
      if (signatureAlgorithmsExtension != null) {
        val sigAlgs =
            new HashSet<SignatureAndHashAlgorithm>(
                TlsExtensionsUtils.getSignatureAlgorithmsExtension(extensions));

        boolean supportsRSA = false;
        boolean supportsECC = false;

        for (SignatureAndHashAlgorithm sigAlg : sigAlgs) {
          if (sigAlg.getSignature() == SignatureAlgorithm.rsa
              || sigAlg.getSignature() == SignatureAlgorithm.rsa_pss_pss_sha256
              || sigAlg.getSignature() == SignatureAlgorithm.rsa_pss_pss_sha384
              || sigAlg.getSignature() == SignatureAlgorithm.rsa_pss_pss_sha512
              || sigAlg.getSignature() == SignatureAlgorithm.rsa_pss_rsae_sha256
              || sigAlg.getSignature() == SignatureAlgorithm.rsa_pss_rsae_sha384
              || sigAlg.getSignature() == SignatureAlgorithm.rsa_pss_rsae_sha512) {
            supportsRSA = true;
          } else if (sigAlg.getSignature() == SignatureAlgorithm.ecdsa
              || sigAlg.getSignature() == SignatureAlgorithm.ecdsa_brainpoolP256r1tls13_sha256
              || sigAlg.getSignature() == SignatureAlgorithm.ecdsa_brainpoolP384r1tls13_sha384
              || sigAlg.getSignature() == SignatureAlgorithm.ecdsa_brainpoolP512r1tls13_sha512) {
            supportsECC = true;
          }
        }

        return calculatePreference(supportsRSA, supportsECC);
      }
    }
    return de.gematik.test.tiger.mockserver.socket.tls.KeyAlgorithmPreference.UNKNOWN;
  }

  private static KeyAlgorithmPreference getKeyAlgorithmFromCipherSuites(int[] cipherSuites) {
    boolean supportsRSA = false;
    boolean supportsECC = false;

    for (int cipherSuite : cipherSuites) {
      int keyExchangeAlgorithm = TlsUtils.getKeyExchangeAlgorithm(cipherSuite);
      switch (keyExchangeAlgorithm) {
        case KeyExchangeAlgorithm.RSA,
        KeyExchangeAlgorithm.RSA_EXPORT,
        KeyExchangeAlgorithm.DHE_RSA,
        KeyExchangeAlgorithm.DHE_RSA_EXPORT,
        KeyExchangeAlgorithm.DH_RSA,
        KeyExchangeAlgorithm.DH_RSA_EXPORT:
          supportsRSA = true;
          break;
        case KeyExchangeAlgorithm.ECDH_ECDSA,
        KeyExchangeAlgorithm.ECDHE_ECDSA,
        KeyExchangeAlgorithm.ECDH_RSA,
        KeyExchangeAlgorithm.ECDHE_RSA,
        KeyExchangeAlgorithm.ECDH_anon:
          supportsECC = true;
          break;
        default:
          break;
      }
    }

    return calculatePreference(supportsRSA, supportsECC);
  }

  private static KeyAlgorithmPreference calculatePreference(
      boolean supportsRSA, boolean supportsECC) {
    if (supportsRSA && supportsECC) {
      return MIXED;
    } else if (supportsRSA) {
      return RSA;
    } else if (supportsECC) {
      return ECC;
    }
    return UNKNOWN;
  }

  // Matrix for determining effective key algorithm preference
  // Rows represent client preferences, columns represent server preferences
  private static final KeyAlgorithmPreference[][] KEY_ALGORITHM_PREFERENCES = {
    // UNKNOWN, MIXED, RSA, ECC
    {UNKNOWN, MIXED, RSA, ECC}, // Client UNKNOWN
    {MIXED, MIXED, RSA, ECC}, // Client MIXED
    {RSA, RSA, RSA, ECC}, // Client RSA
    {ECC, ECC, RSA, ECC} // Client ECC
  };

  public static KeyAlgorithmPreference determineEffectivePreference(
      KeyAlgorithmPreference clientAlgorithmPreference,
      KeyAlgorithmPreference serverAlgorithmPreference) {
    return KEY_ALGORITHM_PREFERENCES[getOrdinal(clientAlgorithmPreference)][
        getOrdinal(serverAlgorithmPreference)];
  }

  private static int getOrdinal(KeyAlgorithmPreference clientAlgorithmPreference) {
    if (clientAlgorithmPreference == null) {
      return UNKNOWN.ordinal();
    }
    return clientAlgorithmPreference.ordinal();
  }

  public boolean matches(TigerPkiIdentity id) {
    val keyAlgorithm = id.findKeyAlgorithm().orElse(null);
    if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
      return this != ECC;
    }
    if ("EC".equalsIgnoreCase(keyAlgorithm) || "ECDSA".equalsIgnoreCase(keyAlgorithm)) {
      return this != RSA;
    }
    return false;
  }
}
