/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.file;

import de.gematik.rbellogger.data.RbelElement;
import org.json.JSONObject;

/**
 * Interface for a listener that is called before a message is saved to a file. This is useful to
 * store metainformation about the message.
 */
public interface RbelFilePreSaveListener {

  /**
   * Callback method that is called for every message. The JSON object is the object that will be
   * saved to the file.
   *
   * @param rbelElement
   * @param jsonObject
   */
  void preSaveCallback(RbelElement rbelElement, JSONObject jsonObject);
}
