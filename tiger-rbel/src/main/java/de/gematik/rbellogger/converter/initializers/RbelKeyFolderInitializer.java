/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter.initializers;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.key.IdentityBackedRbelKey;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
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
                .map(file -> new TigerPkiIdentity(file.getAbsolutePath() + ";00")
                    .withKeyId(Optional.ofNullable(file.getName().split("\\.")[0])))
                .map(IdentityBackedRbelKey::generateRbelKeyPairForIdentity)
                .flatMap(List::stream)
                .forEach(rbelConverter.getRbelKeyManager()::addKey);
        } catch (IOException e) {
            throw new RuntimeException("Error while initializing keys", e);
        }
    }
}
