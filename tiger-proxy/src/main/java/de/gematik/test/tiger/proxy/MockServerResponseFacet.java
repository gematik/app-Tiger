package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import lombok.Value;

@Value
public class MockServerResponseFacet implements RbelFacet {
  HttpRequest httpRequest;
  HttpResponse httpResponse;
}
