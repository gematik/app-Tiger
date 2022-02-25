/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.pki;

import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.common.exceptions.TigerFileSeparatorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

public class TigerPkiIdentityLoader {

    static {
        BouncyCastleJsseProvider bcJsseProv = new BouncyCastleJsseProvider();
        BouncyCastleProvider bcprov = new BouncyCastleProvider();
        Security.addProvider(bcprov);
        Security.addProvider(bcJsseProv);
    }

    /**
     * Loads the described identity. The information string can be
     * "my/file/name.p12;p12password" or
     * "p12password;my/file/name.p12" or
     * "cert.pem;key.pkcs8" or
     * "rsaCert.pem;rsaKey.pkcs1" or
     * "key/store.jks;key" or
     * "key/store.jks;key1;key2" or
     * "key/store.jks;jks;key"
     * <p>
     * Each part can be one of:
     * * filename
     * * password
     * * store-type (accepted are P12, PKCS12, JKS)
     *
     * @param information
     * @return
     */
    public static TigerPkiIdentity loadRbelPkiIdentity(String information) {
        final List<String> informationSplits = Stream.of(information.split(";"))
            .map(String::trim)
            .collect(Collectors.toUnmodifiableList());
        final StoreType storeType = extractStoreType(informationSplits)
            .or(() -> guessStoreType(informationSplits))
            .orElseThrow(() -> new TigerPkiIdentityLoaderException("Unable to determine store-type for input '" + information + "'!"));
        List<Pair<String, byte[]>> fileNamesAndContent = extractFileNames(informationSplits);
        List<String> fileNames = fileNamesAndContent.stream()
            .map(Pair::getLeft)
            .collect(Collectors.toUnmodifiableList());

        if (fileNamesAndContent.isEmpty()
            || (!storeType.isKeystore() && fileNamesAndContent.size() < 2)) {
            throw new IllegalArgumentException("Could not find file information in parameters " +
                "(maybe the files could not be found?)! (" + information + ")");
        }

        if (storeType.isKeystore()) {
            return loadKeystore(fileNamesAndContent.get(0).getRight(), guessPasswordField(informationSplits, fileNames, storeType), storeType.name());
        } else {
            final RbelPkiIdentity rbelPkiIdentity = loadCertKeyPair(storeType, fileNamesAndContent);
            final TigerPkiIdentity tigerPkiIdentity = new TigerPkiIdentity();
            tigerPkiIdentity.setCertificate(rbelPkiIdentity.getCertificate());
            tigerPkiIdentity.setPrivateKey(rbelPkiIdentity.getPrivateKey());
            return tigerPkiIdentity;
        }
    }

    private static String guessPasswordField(List<String> informationSplits, List<String> fileNames, StoreType storeType) {
        return informationSplits.stream()
            .filter(part -> !fileNames.contains(part))
            .filter(part -> !storeType.name().equalsIgnoreCase(part))
            .findAny()
            .orElseThrow(() -> new TigerPkiIdentityLoaderException("Unable to guess password from parts " + informationSplits));
    }

    private static RbelPkiIdentity loadCertKeyPair(StoreType storeType, List<Pair<String, byte[]>> fileNamesAndContent) {
        final byte[] data0 = fileNamesAndContent.get(0).getRight();
        final byte[] data1 = fileNamesAndContent.get(1).getRight();

        if (storeType == StoreType.PKCS1) {
            try {
                return CryptoLoader.getIdentityFromPemAndPkcs1(data0, data1);
            } catch (Exception e) {
                return CryptoLoader.getIdentityFromPemAndPkcs1(data1, data0);
            }
        } else {
            try {
                return CryptoLoader.getIdentityFromPemAndPkcs8(data0, data1);
            } catch (Exception e) {
                return CryptoLoader.getIdentityFromPemAndPkcs8(data1, data0);
            }
        }
    }

    private static List<Pair<String, byte[]>> extractFileNames(List<String> informationSplits) {
        return informationSplits.stream()
            .map(part -> Pair.of(part, loadFileOrResourceData(part)))
            .filter(pair -> pair.getValue().isPresent())
            .map(pair -> Pair.of(pair.getLeft(), pair.getRight().get()))
            .collect(Collectors.toUnmodifiableList());
    }

    private static Optional<StoreType> extractStoreType(List<String> informationSplits) {
        return informationSplits.stream()
            .map(StoreType::findStoreTypeForString)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findAny();
    }

    private static Optional<StoreType> guessStoreType(List<String> informationSplits) {
        return informationSplits.stream()
            .flatMap(part -> Stream.of(part.split("\\.")))
            .map(StoreType::findStoreTypeForString)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findAny();
    }

    private static TigerPkiIdentity loadKeystore(byte[] content, String password, String keystoreType) {
        try {
            KeyStore ks = KeyStore.getInstance(keystoreType);
            ks.load(new ByteArrayInputStream(content), password.toCharArray());
            TigerPkiIdentity result = new TigerPkiIdentity();
            for (Iterator<String> it = ks.aliases().asIterator(); it.hasNext(); ) {
                String alias = it.next();
                if (ks.isKeyEntry(alias)) {
                    result.setCertificate((X509Certificate) ks.getCertificate(alias));
                    result.setPrivateKey((PrivateKey) ks.getKey(alias, password.toCharArray()));
                    final Certificate[] certificateChain = ks.getCertificateChain(alias);
                    for (int i = 1; i < certificateChain.length; i++) {
                        result.addCertificateToCertificateChain((X509Certificate) certificateChain[i]);
                    }
                } else {
                    result.addCertificateToCertificateChain((X509Certificate) ks.getCertificate(alias));
                }
            }
            if (result.getPrivateKey() == null) {
                throw new TigerPkiIdentityLoaderException("Error while loading keystore: No matching entry found!");
            } else {
                return result;
            }
        } catch (Exception e) {
            throw new TigerPkiIdentityLoaderException("Error while loading keystore", e);
        }
    }

    private static Optional<byte[]> loadFileOrResourceData(final String entityLocation) {
        if (StringUtils.isEmpty(entityLocation)) {
            throw new IllegalArgumentException("Trying to load data from empty location! (value is '" + entityLocation + "')");
        }
        if (entityLocation.contains("\\"))  {
            throw new TigerFileSeparatorException("Please use forward slash (/) as a file separator");
        }
        if (!entityLocation.startsWith("classpath:") && new File(entityLocation).exists()) {
            try {
                return Optional.ofNullable(FileUtils.readFileToByteArray(new File(entityLocation)));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        if (entityLocation.startsWith("classpath:")) {
            return loadResourceData(entityLocation.replaceFirst("classpath:", ""));
        } else {
            return loadResourceData(entityLocation);
        }
    }

    private static Optional<byte[]> loadResourceData(String name) {
        try (InputStream rawStream = TigerPkiIdentityLoader.class.getClassLoader().getResourceAsStream(name)) {
            return Optional.ofNullable(rawStream.readAllBytes());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public enum StoreType {
        PKCS12(true, "P12"),
        JKS(true),
        BKS(true),
        PKCS8(false),
        PKCS1(false);

        @Getter
        private final List<String> names;
        @Getter
        private final boolean isKeystore;

        StoreType(boolean isKeystore, String... alternateNames) {
            List<String> names = new ArrayList<>(List.of(alternateNames));
            names.add(name());
            this.names = Collections.unmodifiableList(names);
            this.isKeystore = isKeystore;
        }

        static Optional<StoreType> findStoreTypeForString(final String value) {
            for (StoreType type : values()) {
                for (String candidateName : type.getNames()) {
                    if (candidateName.equalsIgnoreCase(value)) {
                        return Optional.of(type);
                    }
                }
            }

            return Optional.empty();
        }
    }

    public static class TigerPkiIdentityLoaderException extends RuntimeException {
        public TigerPkiIdentityLoaderException(String message, Exception e) {
            super(message, e);
        }

        public TigerPkiIdentityLoaderException(String message) {
            super(message);
        }
    }
}
