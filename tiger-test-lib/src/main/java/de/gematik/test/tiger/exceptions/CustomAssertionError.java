/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.exceptions;

import net.serenitybdd.model.exceptions.CausesAssertionFailure;

// A custom error to wrap the original assertion being thrown by the test and add a custom
// message.
public class CustomAssertionError extends AssertionError implements CausesAssertionFailure {
  public CustomAssertionError(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    // prevent stacktrace to be filled. We don't want to add anything else to the stacktrace.
    return this;
  }
}
