/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
