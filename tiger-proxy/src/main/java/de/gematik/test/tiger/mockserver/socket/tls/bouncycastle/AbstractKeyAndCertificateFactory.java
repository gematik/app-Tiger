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

package de.gematik.test.tiger.mockserver.socket.tls.bouncycastle;

import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public abstract class AbstractKeyAndCertificateFactory implements KeyAndCertificateFactory {

  @Override
  public List<X509Certificate> certificateChain() {
    final List<X509Certificate> result = new ArrayList<>();
    result.add(x509Certificate());
    result.add(certificateAuthorityX509Certificate());
    return result;
  }
}
