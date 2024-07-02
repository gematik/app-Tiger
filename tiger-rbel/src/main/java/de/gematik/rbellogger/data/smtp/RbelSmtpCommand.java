/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.smtp;

public enum RbelSmtpCommand {
  HELO,
  EHLO,
  MAIL,
  RCPT,
  DATA,
  EXPN,
  VRFY,
  HELP,
  RSET,
  NOOP,
  QUIT;
}
