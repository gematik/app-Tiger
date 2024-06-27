/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * Klasse enthält die Daten nach erfolgreicher Schlüsselableitung.
 *
 * @author hve
 */
public class SessionKeyInfo {
  byte[] macKey;
  byte[] encKey;
  byte[] sscMac;
  byte[] sscEnc;

  /** Default-Konstruktor */
  public SessionKeyInfo() {
    encKey = null;
    macKey = null;
    sscEnc = null;
    sscMac = null;
  }

  /**
   * Konstruktor
   *
   * @param alg Der verwendete Ableitungsalgorithmus
   * @param km Die Ableitungsdaten
   */
  public SessionKeyInfo(final DerivationAlgorithm alg, final byte[] km) {
    // (N001.400) b.
    if (alg.equals(DerivationAlgorithm.DESEDE)) {
      encKey = new byte[24];
      sscEnc = new byte[8];
      macKey = new byte[24];
      sscMac = new byte[8];
    }
    // (N001.500) b.
    else if (alg.equals(DerivationAlgorithm.AES128)) {
      encKey = new byte[16];
      sscEnc = new byte[16];
      macKey = new byte[16];
      sscMac = new byte[16];
    }
    // (N001.510) b.
    else if (alg.equals(DerivationAlgorithm.AES192)) {
      encKey = new byte[24];
      sscEnc = new byte[16];
      macKey = new byte[24];
      sscMac = new byte[16];
    }
    // (N001.520) b.
    else if (alg.equals(DerivationAlgorithm.AES256)) {
      encKey = new byte[32];
      sscEnc = new byte[16];
      macKey = new byte[32];
      sscMac = new byte[16];
    }

    System.arraycopy(km, 0, encKey, 0, encKey.length);
    System.arraycopy(km, 0 + encKey.length, sscEnc, 0, sscEnc.length);
    System.arraycopy(km, 0 + encKey.length + sscEnc.length, macKey, 0, macKey.length);
    System.arraycopy(
        km, 0 + encKey.length + sscEnc.length + macKey.length, sscMac, 0, sscMac.length);

    // (N001.400) c.
    // (N001.500) c.
    // (N001.510) c.
    // (N001.520) c.
    // Implizit klar => Keine Aktion
  }

  public byte[] getEncKey() {
    return encKey;
  }

  public void setEncKey(final byte[] key) {
    encKey = key;
  }

  public byte[] getMacKey() {
    return macKey;
  }

  public void setMacKey(final byte[] key) {
    macKey = key;
  }

  public byte[] getSscEnc() {
    return sscEnc;
  }

  public void setSscEnc(final byte[] ssc) {
    sscEnc = ssc;
  }

  public byte[] getSscMac() {
    return sscMac;
  }

  public void setSscMac(final byte[] ssc) {
    sscMac = ssc;
  }
}
