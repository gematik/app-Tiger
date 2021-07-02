/*
 * Copyright (c) 2021 gematik GmbH
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

package de.gematik.test.tiger.common.pki;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class KeyMgr {

    private static final String BEGINPUBKEY_STR = "-----BEGIN PUBLIC KEY-----";

    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    private KeyMgr() {

    }

    public static Key readKeyFromPem(String pem) {
        if (pem.contains(BEGINPUBKEY_STR)) {
            throw new NotImplementedException("Future me - Public keys from PEM is currently not implemented!");
        } else {
            return readPrivateKeyFromPem(pem);
        }
    }

    @SneakyThrows
    public static Certificate readCertificateFromPem(final String pem) {
        var certFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE_PROVIDER);
        final InputStream in = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
        return certFactory.generateCertificate(in);
    }

    @SneakyThrows
    public static Key readPrivateKeyFromPem(String pem) {
        var pemParser = new PEMParser(new StringReader(pem));
        var converter = new JcaPEMKeyConverter();
        return converter.getPrivateKey(PrivateKeyInfo.getInstance(pemParser.readObject()));
    }
}
