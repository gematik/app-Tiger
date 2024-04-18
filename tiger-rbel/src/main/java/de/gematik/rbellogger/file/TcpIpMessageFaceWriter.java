/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.file;

import static de.gematik.rbellogger.file.RbelFileWriter.RECEIVER_HOSTNAME;
import static de.gematik.rbellogger.file.RbelFileWriter.SENDER_HOSTNAME;
import static de.gematik.rbellogger.file.RbelFileWriter.SEQUENCE_NUMBER;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import org.json.JSONObject;

public class TcpIpMessageFaceWriter implements RbelFilePreSaveListener {

  @Override
  public void preSaveCallback(RbelElement rbelElement, JSONObject jsonObject) {
    jsonObject.put(
        SENDER_HOSTNAME,
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getSender)
            .flatMap(element -> element.getFacet(RbelHostnameFacet.class))
            .map(RbelHostnameFacet::toString)
            .orElse(""));
    jsonObject.put(
        RECEIVER_HOSTNAME,
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getReceiver)
            .flatMap(element -> element.getFacet(RbelHostnameFacet.class))
            .map(RbelHostnameFacet::toString)
            .orElse(""));
    jsonObject.put(
        SEQUENCE_NUMBER,
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getSequenceNumber)
            .map(Object::toString)
            .orElse(""));
  }
}
