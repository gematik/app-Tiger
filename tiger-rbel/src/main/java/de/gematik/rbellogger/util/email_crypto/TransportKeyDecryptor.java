/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto;

import com.achelos.egk.g2sim.card.objectsystem.keys.elc.ElcDomainParameter;
import com.achelos.egk.g2sim.card.objectsystem.keys.elc.PrivateElcKey;
import com.achelos.egk.g2sim.exceptions.BcException;
import com.achelos.egk.g2sim.exceptions.ParseException;
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

  private Key determineTransportKey(String oid, String point, String cipher, String mac)
      throws InvalidAttributesException,
          BcException,
          DecoderException,
          ParseException,
          IOException {
    BigInteger privateKeyD = privateKey.getS();
    ElcDomainParameter elcDomainParameter =
        new ElcDomainParameter(ECNamedCurveTable.getName(determineAsn1ObjectIdentifier(oid)));
    PrivateElcKey privateElcKey = new PrivateElcKey(privateKeyD, elcDomainParameter);
    return determineKeyFromHexstring(privateElcKey.decipher(point, cipher, mac));
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
