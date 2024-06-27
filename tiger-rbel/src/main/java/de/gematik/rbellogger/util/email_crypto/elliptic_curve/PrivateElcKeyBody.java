/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

import java.math.BigInteger;
import javax.naming.directory.InvalidAttributesException;

/**
 * Die Klasse beschreibt den Aufbau eines privaten, elliptischen Schlüssels.
 *
 * @author hve
 */
public class PrivateElcKeyBody {

  public static final String KEY_SECRET = "privateKey";
  private BigInteger secret;
  private BigInteger min = BigInteger.ONE;
  private BigInteger max;

  /**
   * Der Konstruktor
   *
   * @param d - Das Secret
   * @throws InvalidAttributesException Wird geworfen wenn die Überprüfung N009.000 fehl schlägt
   */
  public PrivateElcKeyBody(final BigInteger d, final ElcDomainParameter domainParameter)
      throws InvalidAttributesException {

    max = domainParameter.getN().subtract(min);

    // (N009.000)
    if (d.compareTo(min) < 0 || d.compareTo(max) > 0) {
      throw new InvalidAttributesException(
          getClass().getName()
              + " : Secret must be a number in the interval ["
              + min.toString()
              + ", "
              + max.toString()
              + "] (N009.000)");
    }
    this.secret = d;
  }

  public BigInteger getSecret() {
    return this.secret;
  }

  public void setSecret(final BigInteger d, final ElcDomainParameter domainParameter)
      throws InvalidAttributesException {
    max = domainParameter.getN().subtract(min);

    // (N009.000)
    if (d.compareTo(min) < 0 || d.compareTo(max) > 0) {
      throw new InvalidAttributesException(
          getClass().getName()
              + " : Secret must be a number in the interval ["
              + min.toString()
              + ", "
              + max.toString()
              + "] (N009.000)");
    }
    this.secret = d;
  }
}
