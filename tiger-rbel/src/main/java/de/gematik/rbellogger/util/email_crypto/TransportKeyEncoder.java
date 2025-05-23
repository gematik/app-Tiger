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

import java.io.IOException;
import java.util.Arrays;
import lombok.Data;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.DLTaggedObject;

@Data
public class TransportKeyEncoder {

  private static final String TAG_OID_DO_HEX = "06";
  private static final String TAG_CIPHER_DO_HEX = "86";
  private static final String TAG_MAC_DO_HEX = "8E";

  private static final int INDEX_OID_DO = 0;
  private static final int INDEX_KEY_DO = 1;
  private static final int INDEX_CIPHER_DO = 2;
  private static final int INDEX_MAC_DO = 3;
  private static final int FIRST_ARRAY_INDEX = 0;
  private static final int NUMBER_OF_DIGITS_PER_BYTE = 2;
  private static final int CONTENT_START_INDEX = 4;
  private static final int POSITION_AFTER_LEADING_BYTE = 1;

  private byte[] oid;
  private byte[] key;
  private byte[] cipher;
  private byte[] mac;

  public TransportKeyEncoder(final byte[] transportKey) throws IOException, DecoderException {
    parseTransportKey(transportKey);
  }

  private void parseTransportKey(final byte[] transportKey) throws IOException, DecoderException {
    try (ASN1InputStream asn1InputStream = new ASN1InputStream(transportKey)) {
      ASN1Primitive asn1Primitive;
      while ((asn1Primitive = asn1InputStream.readObject()) != null) {

        ASN1TaggedObject cipherAsn1TaggedObject = ASN1TaggedObject.getInstance(asn1Primitive);
        DLSequence cipherSequence = (DLSequence) cipherAsn1TaggedObject.getBaseObject();

        ASN1Encodable asn1Encodable = cipherSequence.getObjectAt(INDEX_OID_DO);
        ASN1ObjectIdentifier objectIdentifier =
            (ASN1ObjectIdentifier) asn1Encodable.toASN1Primitive();
        oid = parseOid(objectIdentifier.getEncoded());

        asn1Encodable = cipherSequence.getObjectAt(INDEX_KEY_DO);
        var keyDoPrimitive = (ASN1TaggedObject) asn1Encodable.toASN1Primitive();
        ASN1Primitive keyCipherDo = keyDoPrimitive.getBaseObject().toASN1Primitive();
        key = parseCipher(keyCipherDo.getEncoded());

        asn1Encodable = cipherSequence.getObjectAt(INDEX_CIPHER_DO);
        DLTaggedObject cipherDoPrimitive = (DLTaggedObject) asn1Encodable.toASN1Primitive();
        cipher = deleteLeadingByteInByteArray(parseCipher(cipherDoPrimitive.getEncoded()));

        asn1Encodable = cipherSequence.getObjectAt(INDEX_MAC_DO);
        DLTaggedObject macDoPrimitive = (DLTaggedObject) asn1Encodable.toASN1Primitive();
        mac = paresMac(macDoPrimitive.getEncoded());
      }
    }
  }

  private byte[] parseOid(final byte[] oidDo) throws DecoderException {
    return parseDataObject(TAG_OID_DO_HEX, oidDo);
  }

  private byte[] parseCipher(final byte[] cipherDo) throws DecoderException {
    return parseDataObject(TAG_CIPHER_DO_HEX, cipherDo);
  }

  private byte[] paresMac(final byte[] macDo) throws DecoderException {
    return parseDataObject(TAG_MAC_DO_HEX, macDo);
  }

  private byte[] parseDataObject(final String tag, final byte[] data) throws DecoderException {
    String dataAsHex = Hex.encodeHexString(data);

    String actualTag = dataAsHex.substring(FIRST_ARRAY_INDEX, NUMBER_OF_DIGITS_PER_BYTE);

    if (!dataAsHex.toUpperCase().startsWith(tag)) {
      throw new RbelDecryptionException("UnrecognizedTag: " + actualTag);
    }

    return Hex.decodeHex(dataAsHex.substring(CONTENT_START_INDEX));
  }

  private byte[] deleteLeadingByteInByteArray(final byte[] cipher) {
    return Arrays.copyOfRange(cipher, POSITION_AFTER_LEADING_BYTE, cipher.length);
  }
}
