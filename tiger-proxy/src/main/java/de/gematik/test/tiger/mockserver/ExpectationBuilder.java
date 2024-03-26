/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver;

import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
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
    mockServer.getHttpState().add(expectation);
    return new Expectation[] {expectation};
  }

  public ExpectationBuilder id(String id) {
    if (id != null) {
      expectation.setId(id);
    }
    return this;
  }
}
