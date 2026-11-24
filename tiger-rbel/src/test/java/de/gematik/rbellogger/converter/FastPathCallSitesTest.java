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
package de.gematik.rbellogger.converter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.jackson.RbelCborConverter;
import de.gematik.rbellogger.facets.jose.RbelJwtConverter;
import de.gematik.rbellogger.facets.jose.RbelJwtFacet;
import de.gematik.rbellogger.facets.uri.RbelUriConverter;
import de.gematik.rbellogger.util.RbelContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The converters look at the first bytes of a value before they start the expensive work, which is
 * only worth it as long as the look is taken. Without these tests, deleting such a look would not
 * make any other test fail.
 */
class FastPathCallSitesTest {

  private static final String JWT =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.UyDCV0XPYxVSSAtyHLU-s995";

  @ParameterizedTest
  @ValueSource(strings = {"", "a", ".a.b", "a..", "!!!.x.y", "ab.cd.ef", "plain.text.value"})
  void aValueThatCannotStartWithAJsonObjectIsNotTriedAsAJwt(String value) {
    var content = spy(RbelContent.of(value.getBytes(UTF_8)));
    var element = RbelElement.create(content, null);

    new RbelJwtConverter().consumeElement(element, null);

    verify(content, never()).countOccurrencesUpTo(anyByte(), anyInt());
    assertThat(element.hasFacet(RbelJwtFacet.class)).isFalse();
  }

  @Test
  void aTokenWhoseHeaderIsAJsonObjectIsTriedAsAJwt() {
    var content = spy(RbelContent.of(JWT.getBytes(UTF_8)));
    var element = RbelElement.create(content, null);

    RbelLogger.build().getRbelConverter().convertElement(element);

    verify(content, atLeastOnce()).countOccurrencesUpTo(anyByte(), anyInt());
  }

  @Test
  void bytesWhoseFirstItemIsCertainlyNoCborAreNotGivenToTheCborParser() {
    var content = spy(RbelContent.of(new byte[] {(byte) 0x82, 0x01}));

    new RbelCborConverter().consumeElement(RbelElement.create(content, null), null);

    verify(content, never()).toInputStream();
  }

  @Test
  void aWellFormedCborItemIsGivenToTheCborParser() {
    var content = spy(RbelContent.of(new byte[] {(byte) 0xA0}));

    new RbelCborConverter().consumeElement(RbelElement.create(content, null), null);

    verify(content, atLeastOnce()).toInputStream();
  }

  @ParameterizedTest
  @ValueSource(strings = {"keep-alive", "gzip, deflate", "Max Mustermann", "3f1b9c0e-77a2-4d19"})
  void aValueThatIsCertainlyNoUriIsNotDecodedToAString(String value) {
    var element = spy(RbelElement.create(RbelContent.of(value.getBytes(UTF_8)), null));

    new RbelUriConverter().consumeElement(element, null);

    verify(element, never()).getRawStringContent();
  }

  @Test
  void aPossibleUriIsDecodedToAString() {
    var element = spy(RbelElement.create(RbelContent.of("/a/b?x=1".getBytes(UTF_8)), null));

    RbelLogger.build().getRbelConverter().convertElement(element);

    verify(element, atLeastOnce()).getRawStringContent();
  }
}
