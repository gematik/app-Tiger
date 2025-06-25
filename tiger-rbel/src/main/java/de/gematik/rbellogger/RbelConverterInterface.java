package de.gematik.rbellogger;

import de.gematik.rbellogger.data.RbelElement;

public interface RbelConverterInterface {

  RbelElement convertElement(RbelElement rbelElement);
}
