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
package de.gematik.rbellogger.facets.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class RbelXmlConverterReaderStateTest {

  @Test
  void theReaderOfAThreadKeepsNeitherTheDocumentNorTheSourceOfTheLastParse() throws Exception {
    var parsed = parseAndForget();

    for (int i = 0; i < 5 && (parsed[0].get() != null || parsed[1].get() != null); i++) {
      System.gc();
      Thread.sleep(50);
    }

    assertThat(parsed[0].get()).as("document of the last parse").isNull();
    assertThat(parsed[1].get()).as("source of the last parse").isNull();
  }

  private static WeakReference<?>[] parseAndForget() throws Exception {
    var source = new InputSource(new StringReader("<a><b>text</b></a>"));
    var document = RbelXmlConverter.parseXml(source);
    assertThat(document.getRootElement().getName()).isEqualTo("a");
    return new WeakReference<?>[] {new WeakReference<>(document), new WeakReference<>(source)};
  }
}
