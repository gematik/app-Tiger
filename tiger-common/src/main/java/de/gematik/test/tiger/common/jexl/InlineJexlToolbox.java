/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

public class InlineJexlToolbox {

    public static String file(String filename) {
        try {
            return Files.readString(Path.of(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String sha256(String value) {
        return Hex.encodeHexString(DigestUtils.sha256(value));
    }

    public String sha256Base64(String value) {
        return Base64.encodeBase64String(DigestUtils.sha256(value));
    }

    public String sha512(String value) {
        return Hex.encodeHexString(DigestUtils.sha512(value));
    }

    public String sha512Base64(String value) {
        return Base64.encodeBase64String(DigestUtils.sha512(value));
    }

    public String md5(String value) {
        return Hex.encodeHexString(DigestUtils.md5(value));
    }

    public String md5Base64(String value) {
        return Base64.encodeBase64String(DigestUtils.md5(value));
    }

    public String base64Encode(String value) {
        return Base64.encodeBase64String(value.getBytes(StandardCharsets.UTF_8));
    }

    public String base64UrlEncode(String value) {
        return Base64.encodeBase64URLSafeString(value.getBytes(StandardCharsets.UTF_8));
    }

    public String base64Decode(String value) {
        return new String(Base64.decodeBase64(value));
    }

    public long currentTimestamp() {
        return Instant.now().getEpochSecond();
    }

    public String currentLocalDateTime() {
        return LocalDateTime.now().toString();
    }

    public String currentZonedDateTime() {
        return ZonedDateTime.now().toString();
    }
}
