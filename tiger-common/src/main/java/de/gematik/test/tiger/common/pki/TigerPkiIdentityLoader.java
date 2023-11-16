/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.pki;

import static de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.StoreType.PKCS12;

import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

public class TigerPkiIdentityLoader {

  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final List<String> DEFAULT_KEYSTORE_PASSWORDS =
      List.of("00", "123456", "gematik", "changeit");

  static {
    BouncyCastleJsseProvider bcJsseProv = new BouncyCastleJsseProvider();
    BouncyCastleProvider bcprov = new BouncyCastleProvider();
    Security.addProvider(bcprov);
    Security.addProvider(bcJsseProv);
  }

  /**
   * Loads the described identity. The information string can be "my/file/name.p12;p12password" or
   * "p12password;my/file/name.p12" or "cert.pem;key.pkcs8" or "rsaCert.pem;rsaKey.pkcs1" or
   * "key/store.jks;key" or "key/store.jks;key1;key2" or "key/store.jks;jks;key"
   *
   * <p>Each part can be one of: * filename * password * store-type (accepted are P12, PKCS12, JKS)
   *
   * @param information the information string a semi colon separated list of values being filename,
   *     password, type
   * @return pki identity object as used within the Tiger platform
   */
  public static TigerPkiIdentity loadRbelPkiIdentity(String information) {
    return loadRbelPkiIdentity(Optional.empty(), information);
  }

  public static TigerPkiIdentity loadRbelPkiIdentity(File file, String information) {
    return loadRbelPkiIdentity(Optional.of(file), information);
  }

  @SneakyThrows
  public static TigerPkiIdentity loadRbelPkiIdentityWithGuessedPassword(File file) {
    Pair<String, byte[]> keystore =
        Pair.of(file.getAbsolutePath(), FileUtils.readFileToByteArray(file));
    final List<String> keystorePasswords = getAllKeystorePasswords();

    for (String password : keystorePasswords) {
      try {
        return loadKeystoreFrom(keystore, password, PKCS12.name());
      } catch (TigerPkiIdentityLoaderException e) {
        // continue
      }
    }

    throw new TigerPkiIdentityLoaderException(
        "Unable to decrypt file %s with any of these keystore passwords: %s"
            .formatted(file.getName(), keystorePasswords));
  }

  static List<String> getAllKeystorePasswords() {
    List<String> keystorePasswords = new ArrayList<>(DEFAULT_KEYSTORE_PASSWORDS);
    List<String> additionalPasswords =
        TigerGlobalConfiguration.readList("tiger", "lib", "additionalKeyStorePasswords");
    keystorePasswords.addAll(additionalPasswords);
    return keystorePasswords;
  }

  private static TigerPkiIdentity loadRbelPkiIdentity(
      Optional<File> fileOptional, String information) {
    final List<String> informationSplits =
        Stream.concat(
                Stream.of(fileOptional)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(File::getAbsolutePath),
                Stream.of(information.split(";")))
            .map(String::trim)
            .toList();
    final StoreType storeType =
        extractStoreType(informationSplits)
            .or(() -> guessStoreType(informationSplits))
            .orElseThrow(
                () ->
                    new TigerPkiIdentityLoaderException(
                        "Unable to determine store-type for input '" + information + "'!"));
    List<Pair<String, byte[]>> fileNamesAndContent =
        fileOptional
            .map(
                f -> {
                  try {
                    return List.of(Pair.of(f.getAbsolutePath(), FileUtils.readFileToByteArray(f)));
                  } catch (IOException e) {
                    throw new IllegalArgumentException(
                        "Error while reading from file '" + f.getAbsolutePath() + "'!", e);
                  }
                })
            .orElseGet(() -> extractFileNames(informationSplits));
    List<String> fileNames = fileNamesAndContent.stream().map(Pair::getLeft).toList();

    if (fileNamesAndContent.isEmpty()
        || (!storeType.isKeystore() && fileNamesAndContent.size() < 2)) {
      throw new IllegalArgumentException(
          "Could not find file information in parameters "
              + "(maybe the files could not be found?)! ("
              + information
              + ")");
    }

    if (storeType.isKeystore()) {
      return loadKeystoreFrom(
          fileNamesAndContent.get(0),
          guessPasswordField(informationSplits, fileNames, storeType),
          storeType.name());
    } else {
      final TigerPkiIdentity rbelPkiIdentity = loadCertKeyPair(storeType, fileNamesAndContent);
      final TigerPkiIdentity tigerPkiIdentity = new TigerPkiIdentity();
      tigerPkiIdentity.setCertificate(rbelPkiIdentity.getCertificate());
      tigerPkiIdentity.setPrivateKey(rbelPkiIdentity.getPrivateKey());
      tigerPkiIdentity.setKeyId(rbelPkiIdentity.getKeyId());
      return tigerPkiIdentity;
    }
  }

  private static String guessPasswordField(
      List<String> informationSplits, List<String> fileNames, StoreType storeType) {
    return informationSplits.stream()
        .filter(part -> !fileNames.contains(part))
        .filter(part -> !storeType.name().equalsIgnoreCase(part))
        .findAny()
        .orElseThrow(
            () ->
                new TigerPkiIdentityLoaderException(
                    "Unable to guess password from parts " + informationSplits));
  }

  private static TigerPkiIdentity loadCertKeyPair(
      StoreType storeType, List<Pair<String, byte[]>> fileNamesAndContent) {
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
        .toList();
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

  private static TigerPkiIdentity loadKeystoreFrom(
      Pair<String, byte[]> keyStore, String password, String keystoreType) {
    try {
      KeyStore ks = KeyStore.getInstance(keystoreType);
      ks.load(new ByteArrayInputStream(keyStore.getRight()), password.toCharArray());
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
        throw new TigerPkiIdentityLoaderException(
            "Error while loading keystore from '"
                + keyStore.getLeft()
                + "': No matching entry found!");
      } else {
        return result;
      }
    } catch (Exception e) {
      throw new TigerPkiIdentityLoaderException(
          "Error while loading keystore from '" + keyStore.getLeft() + "'", e);
    }
  }

  private static Optional<byte[]> loadFileOrResourceData(final String entityLocation) {
    if (StringUtils.isEmpty(entityLocation)) {
      throw new IllegalArgumentException(
          "Trying to load data from empty location! (value is '" + entityLocation + "')");
    }
    String normalizedEntityLocation = FilenameUtils.separatorsToSystem(entityLocation);
    if (!entityLocation.startsWith(CLASSPATH_PREFIX)
        && new File(normalizedEntityLocation).exists()) {
      try {
        return Optional.ofNullable(
            FileUtils.readFileToByteArray(new File(normalizedEntityLocation)));
      } catch (IOException e) {
        return Optional.empty();
      }
    }
    if (entityLocation.startsWith(CLASSPATH_PREFIX)) {
      return loadResourceData(normalizedEntityLocation.replaceFirst(CLASSPATH_PREFIX, ""));
    } else {
      return loadResourceData(normalizedEntityLocation);
    }
  }

  private static Optional<byte[]> loadResourceData(String name) {
    try (InputStream rawStream =
        TigerPkiIdentityLoader.class.getClassLoader().getResourceAsStream(name)) {
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

    @Getter private final List<String> names;
    @Getter private final boolean isKeystore;

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
