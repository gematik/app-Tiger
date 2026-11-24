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
package de.gematik.rbellogger.facets.uri;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.util.RbelContent;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The fast fail may only reject what the converter would not turn into a URI anyway. */
class RbelUriFastFailTest {

  private static boolean fastFails(String value, Charset charset) {
    return RbelUriConverter.isCertainlyNoUri(RbelContent.of(value.getBytes(charset)), charset);
  }

  /** What the converter did before: a URI counts if it has a query, a scheme or a leading slash. */
  private static boolean convertedBefore(String value) {
    try {
      URI uri = new URI(value);
      return uri.getQuery() != null || uri.getScheme() != null || value.startsWith("/");
    } catch (URISyntaxException e) {
      return false;
    }
  }

  @Test
  void theCharactersThatAreNeverLegalMakeJavaNetUriThrowWhereverTheyAre() {
    for (char illegal : " \"<>\\^`{|}".toCharArray()) {
      for (String context :
          List.of("a%sb", "/p%s", "x?%s", "h://host/%s", "#%s", "?q=%s", "h://%sx/", "a:%s")) {
        String value = context.formatted(illegal);
        org.junit.jupiter.api.Assertions.assertThrows(
            URISyntaxException.class, () -> new URI(value), "'" + value + "' should be illegal");
      }
    }
  }

  @Test
  void valuesThatAreNoUriAreRejectedAtOnce() {
    for (String value :
        List.of(
            "",
            "application/json",
            "keep-alive",
            "shop.example.com",
            "3f1b9c0e-77a2-4d19-9a46-0d3f2c1e5b8a",
            "gzip, deflate",
            "Mozilla/5.0 (X11; Linux x86_64) TestClient/1.2",
            "00:1A:2B:3C:4D:5E",
            "2001:db8::1",
            "2026-11-03T12:00:00Z",
            ":foo",
            "a%zzb:/c",
            "a%20b:c",
            "h:/a#b#c",
            "text#a?b",
            "{\"orderId\":4711}",
            "<a>b:c</a>")) {
      assertThat(fastFails(value, UTF_8)).as("'%s'", value).isTrue();
    }
  }

  @Test
  void aScopedIpv6HostWithABarePercentSignIsLeftToJavaNetUri() throws Exception {
    for (String value :
        List.of(
            "http://[fe80::1%en0]:8080/x",
            "http://[fe80::1%25en0]/x", "https://[fe80::abcd%eth0]/a?b=c")) {
      assertThat(new URI(value)).isNotNull();
      assertThat(fastFails(value, UTF_8)).as("'%s'", value).isFalse();
    }
  }

  @Test
  void aPossibleUriGoesThroughToJavaNetUri() {
    for (String value :
        List.of(
            "/a/b",
            "/",
            "http://example.com/x?y=z",
            "a?b=c",
            "?q",
            "mailto:a@b.de",
            "data:text/plain;base64,AAAA",
            "file:///tmp/x",
            "//host/path?x",
            "urn:isbn:0451450523",
            "x/y?z",
            "h:/a#b",
            "/9j/4AAQSkZJRg==")) {
      assertThat(fastFails(value, UTF_8)).as("'%s'", value).isFalse();
    }
  }

  @Test
  void neverRejectsWhatTheConverterWouldHaveTurnedIntoAUri() {
    String alphabet = "abcXYZ019aF:/?#[]@!$&'()*+,;=%-._~ \"<>\\^`{|}\u00e4\u20ac\t";
    var random = new Random(2328);
    for (int i = 0; i < 400_000; i++) {
      int length = random.nextInt(11);
      var value = new StringBuilder();
      for (int j = 0; j < length; j++) {
        value.append(alphabet.charAt(random.nextInt(alphabet.length())));
      }
      for (Charset charset : List.of(UTF_8, ISO_8859_1)) {
        if (fastFails(value.toString(), charset) && convertedBefore(value.toString())) {
          org.junit.jupiter.api.Assertions.fail(
              "'" + value + "' was rejected, but is a URI for " + charset);
        }
      }
    }
  }

  @Test
  void valuesInACharsetThatIsNotAsciiAreLeftToJavaNetUri() {
    for (String charset : List.of("IBM037", "UTF-16LE", "UTF-16BE", "Shift_JIS")) {
      for (String value : List.of("/a/b?x=1", "http://example.com/x?y=z")) {
        assertThat(fastFails(value, Charset.forName(charset)))
            .as("'%s' in %s", value, charset)
            .isFalse();
      }
    }
  }

  @Test
  void theLimitOfTheCheckedPrefixLeavesLongValuesToJavaNetUri() throws Exception {
    String relativePathWhoseQueryStartsPastTheLimit = "x/" + "a".repeat(5000) + "?q=1";
    String escapeThatReachesPastTheLimit = "/" + "a".repeat(4093) + "%41" + "b";

    for (String value :
        List.of(relativePathWhoseQueryStartsPastTheLimit, escapeThatReachesPastTheLimit)) {
      assertThat(new URI(value)).isNotNull();
      assertThat(fastFails(value, UTF_8)).as("value of %d characters", value.length()).isFalse();
    }
    assertThat(fastFails("/ab%4", UTF_8)).isTrue();
    assertThat(fastFails("/ab%", UTF_8)).isTrue();
  }

  @Test
  void theNeverLegalCharactersAreOnlyUsedWhereAByteCannotBePartOfAnotherCharacter() {
    // in Shift_JIS the second byte of a character can be a backslash
    byte[] shiftJis = new byte[] {'/', (byte) 0x83, 0x5C};

    assertThat(
            RbelUriConverter.isCertainlyNoUri(
                RbelContent.of(shiftJis), Charset.forName("Shift_JIS")))
        .isFalse();
  }
}
