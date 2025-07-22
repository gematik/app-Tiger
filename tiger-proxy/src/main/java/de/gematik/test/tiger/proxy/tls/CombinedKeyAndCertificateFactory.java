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
package de.gematik.test.tiger.proxy.tls;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import java.util.*;
import lombok.Data;

@Data
public class CombinedKeyAndCertificateFactory implements KeyAndCertificateFactory {

  private final KeyAndCertificateFactory supplier;
  private final KeyAndCertificateFactory fallback;

  @Override
  public Optional<TigerPkiIdentity> findExactIdentityForHostname(String hostname) {
    return supplier
        .findExactIdentityForHostname(hostname)
        .or(() -> fallback.findExactIdentityForHostname(hostname));
  }

  @Override
  public TigerPkiIdentity resolveIdentityForHostname(String hostname) {
    return findExactIdentityForHostname(hostname)
        .orElseGet(() -> fallback.resolveIdentityForHostname(hostname));
  }
}
