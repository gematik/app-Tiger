/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto;

import eu.europa.esig.dss.validation.reports.Reports;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public final class VerificationResult {
  private final byte[] originalData;
  private final Reports reports;
}
