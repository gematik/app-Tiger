/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.keys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

/*
 * @author jamesdbloom
 */
public class AsymmetricKeyGenerator {

  public static KeyPair createKeyPair(AsymmetricKeyPairAlgorithm algorithm) {
    try {
      KeyPairGenerator generator;
      switch (algorithm) {
        case RSA2048_SHA256:
        case RSA3072_SHA384:
        case RSA4096_SHA512:
          generator = KeyPairGenerator.getInstance(algorithm.getAlgorithm());
          generator.initialize(algorithm.getKeyLength());
          break;
        case EC256_SHA256:
        case EC384_SHA384:
        case ECP512_SHA512:
          generator = KeyPairGenerator.getInstance(algorithm.getAlgorithm());
          generator.initialize(new ECGenParameterSpec(algorithm.getEcDomainParameters()));
          break;
        default:
          throw new IllegalArgumentException(algorithm + " is not a valid key algorithm");
      }
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new RuntimeException(
          "Exception generating key for algorithm \"" + algorithm + "\":" + e.getMessage(), e);
    }
  }
}
