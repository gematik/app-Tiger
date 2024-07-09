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

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * Die Klasse behandelt Fehler die während des Parsingvorgangs auftreten können.
 *
 * @author cdh
 */
public class ParseException extends Exception {

  private static final long serialVersionUID = 2L;

  /**
   * Der Konstruktor
   *
   * @param msg - Die Fehlernachricht
   */
  public ParseException(final String msg) {
    super(msg);
  }
}
