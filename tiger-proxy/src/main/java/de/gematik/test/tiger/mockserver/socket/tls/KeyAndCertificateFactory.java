/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.mockserver.socket.tls;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/*
 * @author jamesdbloom
 */
public interface KeyAndCertificateFactory {

  void buildAndSavePrivateKeyAndX509Certificate();

  boolean certificateNotYetCreated();

  PrivateKey privateKey();

  X509Certificate x509Certificate();

  X509Certificate certificateAuthorityX509Certificate();

  List<X509Certificate> certificateChain();
}
