/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import org.json.JSONObject;

public interface RbelMessagePostProcessor {

  void performMessagePostConversionProcessing(
      RbelElement message, RbelConverter converter, JSONObject messageObject);
}
