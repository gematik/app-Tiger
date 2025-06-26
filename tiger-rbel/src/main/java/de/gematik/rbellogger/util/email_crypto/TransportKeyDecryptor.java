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
package de.gematik.rbellogger.util.email_crypto;

import de.gematik.rbellogger.util.email_crypto.elliptic_curve.BCElc;
import de.gematik.rbellogger.util.email_crypto.elliptic_curve.BcException;
import de.gematik.rbellogger.util.email_crypto.elliptic_curve.ElcDomainParameter;
import de.gematik.rbellogger.util.email_crypto.elliptic_curve.ParseException;
import de.gematik.rbellogger.util.email_crypto.elliptic_curve.PrivateElcKeyBody;
import de.gematik.rbellogger.util.email_crypto.elliptic_curve.StringTools;
import java.io.IOException;
import java.math.BigInteger;
import java.security.Key;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.directory.InvalidAttributesException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;

public class TransportKeyDecryptor {

  private static final int NUMBER_OF_CHARACTERS_PER_BYTE = 2;
  private static final String OID_TAG = "06";
  private static final int TWO_DIGITS = 2;
  private static final String LEADING_ZERO = "0";

  private final ECPrivateKey privateKey;

  public TransportKeyDecryptor(PrivateKey privateKey) {
    this.privateKey = (ECPrivateKey) privateKey;
  }

  public byte[] decryptTransportKey(byte[] encryptedContentKey)
      throws IOException,
          BcException,
          ParseException,
          InvalidAttributesException,
          DecoderException {

    TransportKeyEncoder transportKeyEncoder = new TransportKeyEncoder(encryptedContentKey);
    String oid = Hex.encodeHexString(transportKeyEncoder.getOid());
    String point = Hex.encodeHexString(transportKeyEncoder.getKey());
    String cipher = Hex.encodeHexString(transportKeyEncoder.getCipher());
    String mac = Hex.encodeHexString(transportKeyEncoder.getMac());

    return determineTransportKey(oid, point, cipher, mac).getEncoded();
  }

  private String decipher(
      PrivateElcKeyBody privateElcKey,
      ElcDomainParameter domainParameter,
      String p,
      String c,
      String t)
      throws BcException {
    return StringTools.toHexString(
        BCElc.elcDec(
            privateElcKey,
            domainParameter,
            StringTools.toByteArray(p),
            StringTools.toByteArray(c),
            StringTools.toByteArray(t)));
  }

  private Key determineTransportKey(String oid, String point, String cipher, String mac)
      throws InvalidAttributesException,
          BcException,
          DecoderException,
          IOException,
          ParseException {
    BigInteger privateKeyD = privateKey.getS();
    ElcDomainParameter elcDomainParameter =
        new ElcDomainParameter(ECNamedCurveTable.getName(determineAsn1ObjectIdentifier(oid)));
    var privateElcKey = new PrivateElcKeyBody(privateKeyD, elcDomainParameter);
    return determineKeyFromHexstring(
        decipher(privateElcKey, elcDomainParameter, point, cipher, mac));
  }

  private ASN1ObjectIdentifier determineAsn1ObjectIdentifier(String oidAsHex)
      throws DecoderException, IOException {
    ASN1ObjectIdentifier asn1ObjectIdentifier = null;
    if (oidAsHex != null && oidAsHex.length() > 0) {
      int numberOfBytes = oidAsHex.length() / NUMBER_OF_CHARACTERS_PER_BYTE;
      StringBuilder oidAsn1String = new StringBuilder();
      oidAsn1String.append(OID_TAG);
      String numberOfBytesAsHex = Integer.toHexString(numberOfBytes);
      // dies würde bei OIDs mit einer Länge > 127 Bytes zu einem Fehler führen, solche OIDs kommen
      // in unserem Umfeld jedoch nicht vor
      numberOfBytesAsHex =
          numberOfBytesAsHex.length() < TWO_DIGITS
              ? LEADING_ZERO + numberOfBytesAsHex
              : numberOfBytesAsHex;
      oidAsn1String.append(numberOfBytesAsHex);
      oidAsn1String.append(oidAsHex);
      asn1ObjectIdentifier =
          (ASN1ObjectIdentifier)
              ASN1Primitive.fromByteArray(Hex.decodeHex(oidAsn1String.toString()));
    }
    return asn1ObjectIdentifier;
  }

  private Key determineKeyFromHexstring(String keyAsHex) throws DecoderException {
    byte[] keybytes = Hex.decodeHex(keyAsHex);
    return new SecretKeySpec(keybytes, "AES");
  }
}
