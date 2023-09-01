/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class InlineJexlToolbox {

    public static String file(String filename) {
        try {
            return Files.readString(Path.of(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String resolve(String value) {
        return TigerGlobalConfiguration.resolvePlaceholders(value);
    }

    public String getValue(String variableName) {
        return TigerGlobalConfiguration.readString(variableName, null);
    }

    public String sha256(String value) {
        return value != null ? Hex.encodeHexString(DigestUtils.sha256(value)) : null;
    }

    public String sha256Base64(String value) {
        return value != null ? Base64.encodeBase64String(DigestUtils.sha256(value)) : null;
    }

    public String sha512(String value) {
        return value != null ? Hex.encodeHexString(DigestUtils.sha512(value)) : null;
    }

    public String sha512Base64(String value) {
        return value != null ? Base64.encodeBase64String(DigestUtils.sha512(value)) : null;
    }

    public String md5(String value) {
        return value != null ? Hex.encodeHexString(DigestUtils.md5(value)) : null;
    }

    public String md5Base64(String value) {
        return value != null ? Base64.encodeBase64String(DigestUtils.md5(value)) : null;
    }

    public String base64Encode(String value) {
        return value != null ? Base64.encodeBase64String(value.getBytes(StandardCharsets.UTF_8)) : null;
    }

    public String base64UrlEncode(String value) {
        return value != null ? Base64.encodeBase64URLSafeString(value.getBytes(StandardCharsets.UTF_8)) : null;
    }

    public String base64Decode(String value) {
        return value != null ? new String(Base64.decodeBase64(value)) : null;
    }


    public String randomHex(int size) { return RandomStringUtils.random(size, "abcdef9876543210"); }

    public long currentTimestamp() {
        return Instant.now().getEpochSecond();
    }

    public String currentLocalDate() { return LocalDate.now().toString(); }

    public String currentLocalDateTime() { return LocalDateTime.now().toString(); }

    public String currentZonedDateTime() {
        return ZonedDateTime.now().toString();
    }

    public String urlEncoded(final String value) {
        return value != null ? URLEncoder.encode(value, StandardCharsets.UTF_8) : null;
    }

    public String lowerCase(final String value) { return value != null ? value.toLowerCase() : null; }

    public String upperCase(final String value) { return value != null ? value.toUpperCase() : null; }

    public String subStringAfter(final String value, final String token) {
        return value != null && token != null ? StringUtils.substringAfter(value, token) : null;
    }

    public String subStringBefore(final String value, final String token) {
        return value != null && token != null ? StringUtils.substringBefore(value, token) : null;
    }
}
