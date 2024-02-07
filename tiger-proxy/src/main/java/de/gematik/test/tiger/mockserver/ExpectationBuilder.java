/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver;

import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import de.gematik.test.tiger.mockserver.model.Delay;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import de.gematik.test.tiger.mockserver.netty.MockServer;
import lombok.AllArgsConstructor;

/*
 * @author jamesdbloom
 */
@AllArgsConstructor
public class ExpectationBuilder {

  private final Expectation expectation;
  private final MockServer mockServer;

  public Expectation[] forward(
      final ExpectationForwardAndResponseCallback expectationForwardAndResponseCallback) {
    expectation.thenForward(expectationForwardAndResponseCallback);
    return new Expectation[] {
      mockServer.getHttpState().getRequestMatchers().add(expectation, Cause.API)
    };
  }

  public Expectation[] respond(HttpResponse httpResponse) {
    return respond(httpResponse, Delay.seconds(0));
  }

  public Expectation[] respond(HttpResponse httpResponse, Delay seconds) {
    expectation.thenRespond(httpResponse);
    return new Expectation[] {
      mockServer.getHttpState().getRequestMatchers().add(expectation, Cause.API)
    };
  }
}
