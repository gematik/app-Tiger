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
package de.gematik.rbellogger.data;

import de.gematik.rbellogger.writer.RbelContentType;
import org.apache.commons.lang3.NotImplementedException;
import org.assertj.core.api.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xmlunit.assertj.XmlAssert;

public class RbelSerializationAssertion extends AbstractAssert<RbelSerializationAssertion, String> {

  public RbelSerializationAssertion(String actualSerialization) {
    super(actualSerialization, RbelSerializationAssertion.class);
  }

  public static void assertEquals(
      String expectedSerialization, String actualSerialization, RbelContentType contentType) {
    switch (contentType) {
      case XML -> XmlAssert.assertThat(actualSerialization)
          .and(expectedSerialization)
          .ignoreWhitespace()
          .areIdentical();
      case JSON -> JSONAssert.assertEquals(
          expectedSerialization, actualSerialization, JSONCompareMode.STRICT);
      default -> throw new NotImplementedException(
          "RbelContentType '%s' is not implemented for asserting serialization results."
              .formatted(contentType.toString()));
    }
  }
}
