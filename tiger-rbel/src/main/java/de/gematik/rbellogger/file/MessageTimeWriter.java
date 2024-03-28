/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.file;

import static de.gematik.rbellogger.file.RbelFileWriter.MESSAGE_TIME;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import org.json.JSONObject;

public class MessageTimeWriter implements RbelFilePreSaveListener {

  @Override
  public void preSaveCallback(RbelElement rbelElement, JSONObject jsonObject) {
    rbelElement
        .getFacet(RbelMessageTimingFacet.class)
        .map(RbelMessageTimingFacet::getTransmissionTime)
        .map(Object::toString)
        .ifPresent(str -> jsonObject.put(MESSAGE_TIME, str));
  }
}
