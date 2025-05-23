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
package de.gematik.rbellogger.util.email_crypto;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Certs {

  public static final Path SENDER1_CERT_PEM =
      Paths.get("src/test/resources", "kim/keys/CERT_SENDER1-smcb-enc-vzd02.pem");

  public static final Path SIGNER1_P12 =
      Paths.get("src/test/resources", "kim/keys/SIGNER-smcb-osig-vzd01.p12");

  public static final Path SENDER1_CERT_DER =
      Paths.get("src/test/resources", "kim/keys/CERT_SENDER1-smcb-enc-vzd02.der");

  public static final BigInteger SENDER1_CERT_SN = new BigInteger("306949304460693");

  public static final Path REC1_P12_PATH =
      Paths.get("src/test/resources", "kim/keys/REC1-smcb-enc-vzd01.p12");

  public static final Path ECC_P12 =
      Paths.get("src/test/resources", "kim/keys/2_C.FD.AUT_oid_epa_vau_ecc.p12");

  public static final Path PSYCHO_PROF_PEM =
      Paths.get("src/test/resources", "Psychotherapeut_zwei_prof_E256.pem");
}
