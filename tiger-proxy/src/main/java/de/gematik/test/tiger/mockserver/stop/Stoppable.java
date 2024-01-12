package de.gematik.test.tiger.mockserver.stop;

import java.io.Closeable;

public interface Stoppable extends Closeable {

  void stop();
}
