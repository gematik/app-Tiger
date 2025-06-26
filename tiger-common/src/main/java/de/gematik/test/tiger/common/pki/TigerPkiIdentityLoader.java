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
package de.gematik.test.tiger.common.pki;

import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class TigerPkiIdentityLoader {

  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final List<String> DEFAULT_KEYSTORE_PASSWORDS =
      List.of(
          // common passwords used in keystore-files
          "00",
          "123456",
          "gematik",
          "changeit",
          // some additional common passwords
          "12345678",
          "1234567890",
          "password",
          "qwerty",
          "123456789",
          "12345",
          "abcdef",
          "111111",
          "1234567",
          "password1",
          "letmein",
          "admin",
          "welcome",
          "iloveyou",
          "sunshine",
          "abc123",
          "trustno1",
          "123123",
          "monkey",
          "dragon");

  static {
    TigerSecurityProviderInitialiser.initialize();
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
    return guessStringPartsAndTryToLoadIdentity(information);
  }

  @SneakyThrows
  public static TigerPkiIdentity loadRbelPkiIdentityWithGuessedPassword(File file) {
    Pair<String, byte[]> keystore =
        Pair.of(file.getAbsolutePath(), FileUtils.readFileToByteArray(file));
    final List<String> keystorePasswords = getAllKeystorePasswords();
    final List<String> filenameList = List.of(keystore.getLeft());
    final StoreType storeType = guessStoreType(filenameList).orElseThrow();

    for (String password : keystorePasswords) {
      try {
        return loadKeystoreFrom(
            TigerPkiIdentityInformation.builder()
                .filenames(filenameList)
                .storeType(storeType)
                .password(password)
                .build());
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

  private static TigerPkiIdentity guessStringPartsAndTryToLoadIdentity(String information) {
    final var identityInformation = parseInformationString(information);
    return loadIdentity(identityInformation);
  }

  public static TigerPkiIdentityInformation parseInformationString(String information) {
    val identityInformation = new TigerPkiIdentityInformation();
    identityInformation.setUseCompactFormat(true);
    final List<String> informationSplits =
        Stream.of(information.split(";")).map(String::trim).toList();

    // Store type
    identityInformation.setStoreType(
        extractStoreType(informationSplits)
            .or(() -> guessStoreType(informationSplits))
            .orElseThrow(
                () ->
                    new TigerPkiIdentityLoaderException(
                        "Unable to determine store-type for input '" + information + "'!")));
    List<Pair<String, byte[]>> fileNamesAndContent = extractFileNames(informationSplits);
    identityInformation.setFilenames(fileNamesAndContent.stream().map(Pair::getLeft).toList());

    if (fileNamesAndContent.isEmpty()
        || (!identityInformation.getStoreType().isKeystore() && fileNamesAndContent.size() < 2)) {
      throw new IllegalArgumentException(
          "Could not find file information in parameters "
              + "(maybe the files could not be found?)! ("
              + information
              + ")");
    }

    if (identityInformation.getStoreType().isKeystore()) {
      identityInformation.setPassword(
          guessPasswordField(
                  informationSplits,
                  identityInformation.getFilenames(),
                  identityInformation.getStoreType())
              .orElse(null));
    }
    return identityInformation;
  }

  public static TigerPkiIdentity loadIdentity(TigerPkiIdentityInformation identityInformation) {
    if (identityInformation.getOrGuessStoreType().isKeystore()) {
      if (identityInformation.getPassword() != null) {
        return loadKeystoreFrom(identityInformation);
      } else {
        return getAllKeystorePasswords().stream()
            .map(
                password -> {
                  try {
                    return loadKeystoreFrom(
                        identityInformation.toBuilder().password(password).build());
                  } catch (Exception e) {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(
                () ->
                    new TigerPkiIdentityLoaderException(
                        "No valid password found and could not be guessed for source '"
                            + identityInformation.getFilenames().get(0)
                            + "'"));
      }
    } else {
      return loadCertKeyPair(identityInformation);
    }
  }

  private static Optional<String> guessPasswordField(
      List<String> informationSplits, List<String> fileNames, StoreType storeType) {
    return informationSplits.stream()
        .filter(part -> !fileNames.contains(part))
        .filter(
            part ->
                !storeType.name().equalsIgnoreCase(part)
                    && storeType.getNames().stream().noneMatch(s -> s.equalsIgnoreCase(part)))
        .findAny();
  }

  private static TigerPkiIdentity loadCertKeyPair(TigerPkiIdentityInformation identityInformation) {
    final byte[] data0 = forceReadDataFromLocation(identityInformation.getFilenames().get(0));
    final byte[] data1 = forceReadDataFromLocation(identityInformation.getFilenames().get(1));

    if (identityInformation.getStoreType() == StoreType.PKCS1) {
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
        .map(part -> Pair.of(part, readDataFromLocation(part)))
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

  public static Optional<StoreType> guessStoreType(List<String> informationSplits) {
    return informationSplits.stream()
        .flatMap(part -> Stream.of(part.split("\\.")))
        .map(StoreType::findStoreTypeForString)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findAny();
  }

  private static TigerPkiIdentity loadKeystoreFrom(
      TigerPkiIdentityInformation identityInformation) {
    val sourceUri = identityInformation.getFilenames().get(0);
    val keystoreInputStream =
        readDataFromLocation(sourceUri)
            .map(ByteArrayInputStream::new)
            .orElseThrow(
                () ->
                    new TigerPkiIdentityLoaderException(
                        "Unable to load keystore from '" + sourceUri + "'!"));
    try {
      KeyStore ks = KeyStore.getInstance(identityInformation.getOrGuessStoreType().name());
      ks.load(keystoreInputStream, identityInformation.getPassword().toCharArray());
      TigerPkiIdentity result = new TigerPkiIdentity();
      for (Iterator<String> it = ks.aliases().asIterator(); it.hasNext(); ) {
        String alias = it.next();
        if (ks.isKeyEntry(alias)) {
          result.setCertificate((X509Certificate) ks.getCertificate(alias));
          result.setPrivateKey(
              (PrivateKey) ks.getKey(alias, identityInformation.getPassword().toCharArray()));
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
            "Error while loading keystore from '" + sourceUri + "': No matching entry found!");
      } else {
        return result;
      }
    } catch (Exception e) {
      throw new TigerPkiIdentityLoaderException(
          "Error while loading keystore from '"
              + sourceUri
              + "' with password '"
              + identityInformation.getPassword()
              + "'",
          e);
    }
  }

  private static byte[] forceReadDataFromLocation(String entityLocation) {
    return readDataFromLocation(entityLocation)
        .orElseThrow(
            () ->
                new TigerPkiIdentityLoaderException(
                    "Unable to load data from location '" + entityLocation + "'!"));
  }

  private static Optional<byte[]> readDataFromLocation(String entityLocation) {
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
      List<String> namesList = new ArrayList<>(List.of(alternateNames));
      namesList.add(name());
      this.names = Collections.unmodifiableList(namesList);
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
