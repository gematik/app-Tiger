package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import lombok.Value;

@Value
public class MockServerRequestFacet implements RbelFacet {
  HttpRequest httpRequest;
}
