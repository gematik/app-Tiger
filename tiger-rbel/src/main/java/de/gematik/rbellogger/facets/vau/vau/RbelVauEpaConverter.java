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
package de.gematik.rbellogger.facets.vau.vau;

import static de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet.KEY_ID_LENGTH;
import static de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet.keyIdHexOf;
import static de.gematik.rbellogger.util.CryptoUtils.decryptUnsafe;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.http.RbelHttpHeaderFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@ConverterInfo(
    onlyActivateFor = "epa-vau",
    dependsOn = {RbelVauEpaKeyDeriver.class})
@Slf4j
public class RbelVauEpaConverter extends RbelConverterPlugin {

  private static final int BASE64_KEY_ID_PREFIX_LENGTH = 44;

  @Override
  public void consumeElement(RbelElement element, RbelConversionExecutor context) {
    context.waitForAllElementsBeforeGivenToBeParsed(element.findRootElement());
    tryToExtractKeyIdHex(element)
        .flatMap(keyIdHex -> decipherVauMessage(keyIdHex, context, element))
        .ifPresent(
            vauMsg -> {
              element.addFacet(vauMsg);
              element.addFacet(new RbelRootFacet<>(vauMsg));
              context.convertElement(vauMsg.getMessage());
            });
  }

  private static boolean isJsonChild(RbelElement element) {
    return element.getParentNode() != null && element.getParentNode().hasFacet(RbelJsonFacet.class);
  }

  private Optional<String> tryToExtractKeyIdHex(RbelElement element) {
    final RbelContent content = element.getContent();
    if (content == null || content.isNull()) {
      return Optional.empty();
    }
    return isJsonChild(element) ? keyIdOfBase64(content) : keyIdOfRaw(content);
  }

  private static Optional<String> keyIdOfRaw(RbelContent content) {
    return content.size() < KEY_ID_LENGTH ? Optional.empty() : Optional.of(keyIdHexOf(content));
  }

  private static Optional<String> keyIdOfBase64(RbelContent content) {
    try {
      final byte[] prefix =
          Base64.getDecoder()
              .decode(
                  content.toByteArray(0, Math.min(content.size(), BASE64_KEY_ID_PREFIX_LENGTH)));
      return prefix.length < KEY_ID_LENGTH
          ? Optional.empty()
          : Optional.of(Hex.toHexString(prefix, 0, KEY_ID_LENGTH));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private Optional<RbelContent> tryToExtractEncryptedPart(RbelElement element) {
    final RbelContent content = element.getContent();
    if (!isJsonChild(element)) {
      return Optional.of(content.subArray(KEY_ID_LENGTH, content.size()));
    }
    try {
      final InputStream decoded = Base64.getDecoder().wrap(content.toInputStream());
      decoded.skipNBytes(KEY_ID_LENGTH);
      return Optional.of(RbelContent.from(decoded));
    } catch (IllegalArgumentException | IOException e) {
      return Optional.empty();
    }
  }

  private Optional<RbelVauEpaFacet> decipherVauMessage(
      String keyIdHex, RbelConversionExecutor converter, RbelElement parentNode) {
    final List<RbelKey> potentialVauKeys =
        converter
            .getRbelKeyManager()
            .getAllKeys()
            .filter(key -> key.getKeyName().startsWith(keyIdHex))
            .filter(key -> key.getKey() instanceof SecretKey)
            .toList();
    if (potentialVauKeys.isEmpty()) {
      markAsUndecipherable(parentNode, noMatchingKeyNotes(keyIdHex, converter));
      return Optional.empty();
    }
    final Optional<RbelContent> encryptedPart = tryToExtractEncryptedPart(parentNode);
    if (encryptedPart.isEmpty()) {
      return Optional.empty();
    }

    List<RbelNoteFacet> errorNotes = new ArrayList<>();
    for (RbelKey rbelKey : potentialVauKeys) {
      Optional<byte[]> decryptedBytes;
      try {
        decryptedBytes =
            Optional.ofNullable(
                decryptUnsafe(
                    encryptedPart.get(),
                    rbelKey.getKey(),
                    CryptoUtils.GCM_IV_LENGTH_IN_BYTES,
                    CryptoUtils.GCM_TAG_LENGTH_IN_BYTES));
      } catch (GeneralSecurityException e) {
        errorNotes.add(
            RbelNoteFacet.builder()
                .style(NoteStyling.ERROR)
                .value("Error during decryption: " + e.getMessage())
                .build());
        decryptedBytes = Optional.empty();
      }
      if (decryptedBytes.isPresent()) {
        try {
          if (log.isTraceEnabled()) {
            log.trace("Succesfully deciphered VAU message! ({})", new String(decryptedBytes.get()));
          }
          return buildVauMessageFromCleartext(
              converter, keyIdHex, encryptedPart.get(), decryptedBytes.get(), parentNode, rbelKey);
        } catch (RuntimeException e) {
          log.error("Exception while building cleartext VAU message:", e);
          throw new RbelConversionException("Exception while building cleartext VAU message", e);
        }
      }
    }
    markAsUndecipherable(parentNode, errorNotes);
    return Optional.empty();
  }

  private static List<RbelNoteFacet> noMatchingKeyNotes(
      String keyIdHex, RbelConversionExecutor converter) {
    return List.of(
        RbelNoteFacet.builder()
            .value(
                "Found no matching key! (Was the handshake logged?) key-name: '" + keyIdHex + "'")
            .style(NoteStyling.WARN)
            .build(),
        RbelNoteFacet.builder()
            .style(NoteStyling.INFO)
            .value(
                "Known keys: <br/>"
                    + converter
                        .getRbelKeyManager()
                        .getAllKeys()
                        .map(RbelKey::getKeyName)
                        .collect(Collectors.joining("<br/>")))
            .build());
  }

  private static void markAsUndecipherable(RbelElement element, List<RbelNoteFacet> errorNotes) {
    if (element.getParentNode() != null
        && element.getParentNode().hasFacet(RbelHttpMessageFacet.class)
        && element.getFacets().isEmpty()) {
      element.addFacet(new RbelUndecipherableVauEpaFacet(errorNotes));
    }
  }

  private Optional<RbelVauEpaFacet> buildVauMessageFromCleartext(
      RbelConversionExecutor converter,
      String keyIdHex,
      RbelContent encryptedPart,
      byte[] decryptedBytes,
      RbelElement parentNode,
      RbelKey rbelKey) {
    final String cleartextString = new String(decryptedBytes);
    if (cleartextString.startsWith("VAUClientSigFin")
        || cleartextString.startsWith("VAUServerFin")) {
      final RbelElement decryptedPayload = new RbelElement(decryptedBytes, parentNode);
      decryptedPayload.addFacet(new RbelBinaryFacet());
      return Optional.of(
          RbelVauEpaFacet.builder()
              .message(decryptedPayload)
              .encryptedMessage(RbelElement.create(encryptedPart, parentNode))
              .keyIdUsed(RbelElement.wrap(parentNode, rbelKey.getKeyName().split("_")[0]))
              .keyUsed(Optional.of(rbelKey))
              .build());
    } else {
      return Optional.of(
          fromRaw(keyIdHex, encryptedPart, converter, decryptedBytes, parentNode, rbelKey));
    }
  }

  private RbelVauEpaFacet fromRaw(
      String keyIdHex,
      RbelContent encryptedPart,
      RbelConversionExecutor converter,
      byte[] decryptedBytes,
      RbelElement parentNode,
      RbelKey rbelKey) {
    byte[] raw = new byte[decryptedBytes.length - 8 - 1];
    System.arraycopy(decryptedBytes, 8 + 1, raw, 0, raw.length);

    byte[] sequenceNumberBytes = new byte[4];
    System.arraycopy(decryptedBytes, 5, sequenceNumberBytes, 0, 4);
    int sequenceNumber = java.nio.ByteBuffer.wrap(sequenceNumberBytes).getInt();

    byte[] pHeaderInformation = new byte[1 + 8 + 4];
    System.arraycopy(decryptedBytes, 0, pHeaderInformation, 0, pHeaderInformation.length);

    byte[] numberOfBytesInBytes = new byte[4];
    System.arraycopy(raw, 0, numberOfBytesInBytes, 0, 4);
    int numberOfBytes = java.nio.ByteBuffer.wrap(numberOfBytesInBytes).getInt();

    byte[] headerFieldInBytes = new byte[numberOfBytes];
    System.arraycopy(raw, 4, headerFieldInBytes, 0, numberOfBytes);
    String headerField = new String(headerFieldInBytes, StandardCharsets.US_ASCII);

    RbelElement headerElement = new RbelElement(headerFieldInBytes, parentNode);
    RbelHttpHeaderFacet header = new RbelHttpHeaderFacet();
    headerElement.addFacet(header);
    Arrays.stream(headerField.split("\r\n"))
        .map(field -> field.split(":", 2))
        .forEach(
            field ->
                header.put(field[0].trim(), converter.convertElement(field[1], headerElement)));

    byte[] body = new byte[raw.length - 4 - numberOfBytes];
    System.arraycopy(raw, 4 + numberOfBytes, body, 0, body.length);

    final String pHeaderHexString =
        String.join(" ", Hex.toHexString(pHeaderInformation).split("(?<=\\G.{2})"));
    return RbelVauEpaFacet.builder()
        .message(new RbelElement(body, parentNode))
        .additionalHeaders(headerElement)
        .encryptedMessage(RbelElement.create(encryptedPart, parentNode))
        .keyIdUsed(RbelElement.wrap(parentNode, keyIdHex))
        .pVersionNumber(RbelElement.wrap(parentNode, (int) decryptedBytes[0]))
        .sequenceNumber(RbelElement.wrap(parentNode, (long) sequenceNumber))
        .keyUsed(Optional.ofNullable(rbelKey))
        .pHeaderInformation(RbelElement.wrap(parentNode, pHeaderHexString))
        .build();
  }
}
