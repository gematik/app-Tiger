/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.common.jexl;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("unused")
public class InlineJexlToolbox {
  /**
   * reads file - not post-processed, no variable substitution
   *
   * @param filename path to file
   * @return content of the file
   */
  @SneakyThrows
  public static String file(String filename) {
    return Files.readString(Path.of(filename));
  }

  /**
   * parses the given string value and resolves / substitutes all placeholder tokens of the form
   * ${...} and !{...}
   *
   * @param value string to be resolved
   * @return Tiger globalConfigurationLoader
   */
  public String resolve(String value) {
    return TigerGlobalConfiguration.resolvePlaceholders(value);
  }

  /**
   * reads the value of the variable from TigerGlobalConfiguration
   *
   * @param variableName name of the variable
   * @return value of the property of the TigerGlobalConfiguration
   */
  public String getValue(String variableName) {
    return TigerGlobalConfiguration.readString(variableName, null);
  }

  /**
   * reads string value and transforms to SHA256 format
   *
   * @param value string to be read
   * @return encoded value in SHA256 format
   */
  public String sha256(String value) {
    return value != null ? Hex.encodeHexString(DigestUtils.sha256(value)) : null;
  }

  /**
   * reads string value and transforms to SHA512
   *
   * @param value string to be read
   * @return encoded values in SHA512 format
   */
  public String sha512(String value) {
    return value != null ? Hex.encodeHexString(DigestUtils.sha512(value)) : null;
  }

  /**
   * reads string value and transforms to base64 of sha512 hash
   *
   * @param value string to be read
   * @return encoded sha512 value in base64 format
   */
  public String sha512Base64(String value) {
    return value != null ? Base64.encodeBase64String(DigestUtils.sha512(value)) : null;
  }

  /**
   * reads string value and transforms to md5
   *
   * @param value string to be read
   * @return encoded values in md5 format
   */
  public String md5(String value) {
    return value != null ? Hex.encodeHexString(DigestUtils.md5(value)) : null;
  }

  /**
   * reads string value and transforms to base64 of the md5 hash
   *
   * @param value string to be read
   * @return if value is not null, encoded md5 values in base64 format are returned, else null
   */
  public String md5Base64(String value) {
    return value != null ? Base64.encodeBase64String(DigestUtils.md5(value)) : null;
  }

  /**
   * reads string value and transforms to base64
   *
   * @param value string to be read
   * @return if value is not null, base64 encoded values in UTF8 format are returned, else null
   */
  public String base64Encode(String value) {
    return value != null ? Base64.encodeBase64String(value.getBytes(StandardCharsets.UTF_8)) : null;
  }

  /**
   * reads string value and safely transforms to url encoded base64
   *
   * @param value string to be read
   * @return if value is not null, base64 encoded values in ASCII and URL safe format are returned,
   *     else null
   */
  public String base64UrlEncode(String value) {
    return value != null
        ? Base64.encodeBase64URLSafeString(value.getBytes(StandardCharsets.UTF_8))
        : null;
  }

  /**
   * reads base64 encoded string value and safely decodes to string
   *
   * @param value base64 encoded string to be read
   * @return if value is not null, base64 decoded value is returned as String, else null
   */
  public String base64Decode(String value) {
    return value != null ? new String(Base64.decodeBase64(value)) : null;
  }

  /**
   * array of chars
   *
   * @param size array size to be returned
   * @return array of random hex chars of given size
   */
  public String randomHex(int size) {
    return RandomStringUtils.random(size, "abcdef9876543210");
  }

  /**
   * url encodes a string
   *
   * @param value final String
   * @return if value not null, the url encoded representation of the string is returned
   */
  public String urlEncoded(final String value) {
    return value != null ? URLEncoder.encode(value, StandardCharsets.UTF_8) : null;
  }

  /**
   * implements String.substringAfter method,
   *
   * @param value string to get sub string of
   * @param token token to look for in the search string
   * @return if value not null, the substring that comes after the first occurrence of the given
   *     token is returned
   */
  public String subStringAfter(final String value, final String token) {
    return value != null && token != null ? StringUtils.substringAfter(value, token) : null;
  }

  /**
   * implements String.substringBefore method
   *
   * @param value string to get sub string of
   * @param token token to look for in the search string
   * @return if value not null, the substring that comes before the first occurrence of the given
   *     token is returned
   */
  public String subStringBefore(final String value, final String token) {
    return value != null && token != null ? StringUtils.substringBefore(value, token) : null;
  }

  /**
   * reads string value and transforms to base64
   *
   * @param value SHA256 value
   * @return encoded value in base64 format
   */
  public String sha256Base64(String value) {
    return value != null ? Base64.encodeBase64String(DigestUtils.sha256(value)) : null;
  }

  /**
   * logs current time
   *
   * @return current time in seconds
   */
  public long currentTimestamp() {
    return Instant.now().getEpochSecond();
  }

  /**
   * logs current date
   *
   * @return current date in LocalDate-format
   */
  public String currentLocalDate() {
    return LocalDate.now().toString();
  }

  /**
   * logs current datetime
   *
   * @return current date in LocalDateTime-format
   */
  public String currentLocalDateTime() {
    return LocalDateTime.now().toString();
  }

  /**
   * logs current datetime including timezone
   *
   * @return current date in ZonedDateTime-format
   */
  public String currentZonedDateTime() {
    return ZonedDateTime.now().toString();
  }

  /**
   * transforms string to lower case
   *
   * @param value final String
   * @return if value not null, the input string is returned in lower case letters
   */
  public String lowerCase(final String value) {
    return value != null ? value.toLowerCase() : null;
  }

  /**
   * transforms string to lower case
   *
   * @param value final String
   * @return if value not null, the input string is returned in upper case letters
   */
  public String upperCase(final String value) {
    return value != null ? value.toUpperCase() : null;
  }
}
