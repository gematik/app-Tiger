/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/** Die Klasse beschreibt das Attribut 'ObjectIdentifier' gemäß 7.1.1.5 */
public class ObjectIdentifier {

  public static final String ANSIX9P256R1 = "ansix9p256r1";
  public static final String ANSIX9P384R1 = "ansix9p384r1";
  public static final String SECP256R1 = "secp256r1";
  public static final String SECP384R1 = "secp384r1";
  public static final String BRAINPOOLP256R1 = "brainpoolP256r1";
  public static final String BRAINPOOLP384R1 = "brainpoolP384r1";
  public static final String BRAINPOOLP512R1 = "brainpoolP512r1";

  private ObjectIdentifier() {}
}
