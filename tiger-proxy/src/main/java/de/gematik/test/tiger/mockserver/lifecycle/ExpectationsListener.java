package de.gematik.test.tiger.mockserver.lifecycle;

import de.gematik.test.tiger.mockserver.mock.Expectation;
import java.util.List;

public interface ExpectationsListener {

  void updated(List<Expectation> expectations);
}
