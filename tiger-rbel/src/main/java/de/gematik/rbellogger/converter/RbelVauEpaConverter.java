/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.util.CryptoUtils.decryptUnsafe;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.facet.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoUtils;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class RbelVauEpaConverter implements RbelConverterPlugin {

    @Override
    public void consumeElement(RbelElement element, RbelConverter context) {
        log.trace("Trying to decipher '{}'...", element.getRawStringContent());
        tryToExtractRawVauContent(element)
            .flatMap(content -> decipherVauMessage(content, context, element))
            .ifPresent(vauMsg -> {
                element.addFacet(vauMsg);
                element.addFacet(new RbelRootFacet<>(vauMsg));
                context.convertElement(vauMsg.getMessage());
            });
    }

    private Optional<byte[]> tryToExtractRawVauContent(RbelElement element) {
        if (element.getParentNode() != null
            && element.getParentNode().hasFacet(RbelJsonFacet.class)) {
            try {
                return Optional.ofNullable(Base64.getDecoder().decode(element.getRawContent()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        } else {
            return Optional.ofNullable(element.getRawContent());
        }
    }

    private Optional<RbelVauEpaFacet> decipherVauMessage(byte[] content, RbelConverter converter,
        RbelElement parentNode) {
        List<RbelNoteFacet> errorNotes = new ArrayList<>();
        final Optional<Pair<byte[], byte[]>> splitOptional = splitVauMessage(content);
        if (splitOptional.isEmpty()) {
            return Optional.empty();
        }
        final Pair<byte[], byte[]> splitVauMessage = splitOptional.get();
        final List<RbelKey> potentialVauKeys = converter.getRbelKeyManager().getAllKeys()
            .filter(key -> key.getKeyName().startsWith(Hex.toHexString(splitVauMessage.getKey())))
            .filter(key -> key.getKey() instanceof SecretKey)
            .collect(Collectors.toList());

        for (RbelKey rbelKey : potentialVauKeys) {
            Optional<byte[]> decryptedBytes;
            try {
                decryptedBytes = Optional.ofNullable(decryptUnsafe(splitVauMessage.getValue(), rbelKey.getKey(),
                    CryptoUtils.GCM_IV_LENGTH_IN_BYTES, CryptoUtils.GCM_TAG_LENGTH_IN_BYTES));
            } catch (GeneralSecurityException e) {
                errorNotes.add(RbelNoteFacet.builder()
                    .style(NoteStyling.ERROR)
                    .value("Error during decryption: " + e.getMessage())
                    .build());
                decryptedBytes = Optional.empty();
            }
            if (decryptedBytes.isPresent()) {
                try {
                    log.trace("Succesfully deciphered VAU message! ({})", new String(decryptedBytes.get()));
                    return buildVauMessageFromCleartext(converter, splitVauMessage, decryptedBytes.get(),
                        parentNode, rbelKey);
                } catch (RuntimeException e) {
                    log.error("Exception while building cleartext VAU message:", e);
                    throw new RbelConversionException("Exception while building cleartext VAU message", e);
                }
            }
        }
        if (potentialVauKeys.isEmpty()) {
            errorNotes.add(RbelNoteFacet.builder()
                .value("Found no matching key! (Was the handshake logged?) key-name: '"
                    + Hex.toHexString(splitVauMessage.getKey()) + "'")
                .style(NoteStyling.WARN)
                .build());
            errorNotes.add(RbelNoteFacet.builder()
                .style(NoteStyling.INFO)
                .value("Known keys: <br/>" +
                    converter.getRbelKeyManager().getAllKeys()
                        .map(RbelKey::getKeyName)
                        .collect(Collectors.joining("<br/>")))
                .build());
        }
        if (parentNode.getParentNode() != null
            && parentNode.getParentNode().hasFacet(RbelHttpMessageFacet.class)) {
            parentNode.addFacet(new RbelUndecipherableVauEpaFacet(errorNotes));
        }
        return Optional.empty();
    }

    private Optional<RbelVauEpaFacet> buildVauMessageFromCleartext(RbelConverter converter,
        Pair<byte[], byte[]> splitVauMessage,
        byte[] decryptedBytes, RbelElement parentNode, RbelKey rbelKey) {
        final String cleartextString = new String(decryptedBytes);
        if (cleartextString.startsWith("VAUClientSigFin")
            || cleartextString.startsWith("VAUServerFin")) {
            final RbelElement decryptedPayload = new RbelElement(decryptedBytes, parentNode);
            decryptedPayload.addFacet(new RbelBinaryFacet());
            return Optional.of(RbelVauEpaFacet.builder()
                .message(converter.filterInputThroughPreConversionMappers(decryptedPayload))
                .encryptedMessage(RbelElement.wrap(parentNode, splitVauMessage.getValue()))
                .keyIdUsed(RbelElement.wrap(parentNode, rbelKey.getKeyName().split("_")[0]))
                .keyUsed(Optional.of(rbelKey))
                .build());
        } else {
            return Optional.of(fromRaw(splitVauMessage, converter, decryptedBytes, parentNode, rbelKey));
        }
    }

    private RbelVauEpaFacet fromRaw(Pair<byte[], byte[]> payloadPair, RbelConverter converter,
        byte[] decryptedBytes, RbelElement parentNode, RbelKey rbelKey) {
        byte[] raw = new byte[decryptedBytes.length - 8 - 1];
        System.arraycopy(decryptedBytes, 8 + 1, raw, 0, raw.length);

        byte[] sequenceNumberBytes = new byte[4];
        System.arraycopy(decryptedBytes, 5, sequenceNumberBytes, 0, 4);
        int sequenceNumber = java.nio.ByteBuffer.wrap(sequenceNumberBytes).getInt();

        byte[] numberOfBytes_inBytes = new byte[4];
        System.arraycopy(raw, 0, numberOfBytes_inBytes, 0, 4);
        int numberOfBytes = java.nio.ByteBuffer.wrap(numberOfBytes_inBytes).getInt();

        byte[] headerField_inBytes = new byte[numberOfBytes];
        System.arraycopy(raw, 4, headerField_inBytes, 0, numberOfBytes);
        String headerField = new String(headerField_inBytes, StandardCharsets.US_ASCII);

        RbelElement headerElement = new RbelElement(headerField_inBytes, parentNode);
        RbelHttpHeaderFacet header = new RbelHttpHeaderFacet();
        headerElement.addFacet(header);
        Arrays.stream(headerField.split("\r\n"))
            .map(field -> field.split(":", 2))
            .forEach(field -> header.put(field[0].trim(), converter.convertElement(field[1], headerElement)));

        byte[] body = new byte[raw.length - 4 - numberOfBytes];
        System.arraycopy(raw, 4 + numberOfBytes, body, 0, body.length);

        return RbelVauEpaFacet.builder()
            .message(converter.filterInputThroughPreConversionMappers(new RbelElement(body, parentNode)))
            .additionalHeaders(headerElement)
            .encryptedMessage(RbelElement.wrap(payloadPair.getValue(), parentNode, null))
            .keyIdUsed(RbelElement.wrap(parentNode, Hex.toHexString(payloadPair.getKey())))
            .pVersionNumber(RbelElement.wrap(parentNode, (int) decryptedBytes[0]))
            .sequenceNumber(RbelElement.wrap(parentNode, (long) sequenceNumber))
            .keyUsed(Optional.ofNullable(rbelKey))
            .build();
    }

    private Optional<Pair<byte[], byte[]>> splitVauMessage(byte[] vauMessage) {
        try {
            byte[] keyID = new byte[32];
            System.arraycopy(vauMessage, 0, keyID, 0, 32);
            byte[] enc = new byte[vauMessage.length - 32];
            System.arraycopy(vauMessage, 32, enc, 0, vauMessage.length - 32);
            return Optional.of(Pair.of(keyID, enc));
        } catch (ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }
}
