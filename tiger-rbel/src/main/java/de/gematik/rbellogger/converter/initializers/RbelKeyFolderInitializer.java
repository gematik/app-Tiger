/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.rbellogger.converter.initializers;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@RequiredArgsConstructor
public class RbelKeyFolderInitializer implements Consumer<RbelConverter> {

    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    private final String keyFolderPath;

    @Override
    public void accept(RbelConverter rbelConverter) {
        try (final Stream<Path> fileStream = Files.walk(Path.of(keyFolderPath))) {
            fileStream
                .map(Path::toFile)
                .filter(File::isFile)
                .filter(File::canRead)
                .filter(file -> file.getName().endsWith(".p12"))
                .map(this::readFileToKeyList)
                .flatMap(List::stream)
                .forEach(rbelConverter.getRbelKeyManager()::addKey);
        } catch (IOException e) {
            throw new RuntimeException("Error while initializing keys", e);
        }
    }

    private List<RbelKey> readFileToKeyList(File file) {
        try {
            return getIdentityFromP12(FileUtils.readFileToByteArray(file),
                file.getName().replace(".p12", ""));
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<RbelKey> getIdentityFromP12(final byte[] p12FileContent, final String fileName) {
        try {
            final KeyStore p12 = KeyStore.getInstance("pkcs12", BOUNCY_CASTLE_PROVIDER);
            p12.load(new ByteArrayInputStream(p12FileContent), "00".toCharArray());

            final Enumeration<String> e = p12.aliases();
            while (e.hasMoreElements()) {
                final String alias = e.nextElement();
                final X509Certificate certificate = (X509Certificate) p12.getCertificate(alias);
                final PrivateKey privateKey = (PrivateKey) p12.getKey(alias, "00".toCharArray());
                final RbelKey rbelPublicKey = new RbelKey(certificate.getPublicKey(), "puk_" + fileName,
                    RbelKey.PRECEDENCE_KEY_FOLDER);
                return List.of(rbelPublicKey,
                    new RbelKey(privateKey, "prk_" + fileName, RbelKey.PRECEDENCE_KEY_FOLDER, rbelPublicKey));
            }
            return List.of();
        } catch (final IOException | KeyStoreException | NoSuchAlgorithmException
            | UnrecoverableKeyException | CertificateException e) {
            return List.of();
        }
    }
}
