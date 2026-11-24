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
package de.gematik.rbellogger.facets.pki.base64;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.util.Base64Alphabet;
import de.gematik.rbellogger.util.RbelContent;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

public class RbelBase64JsonConverter extends RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbel, RbelConversionExecutor context) {
    if (rbel.getSize() == 0 || rbel.hasFacet(RbelRootFacet.class)) {
      return;
    }
    var content = rbel.getContent();
    if (!Base64Alphabet.canBeBase64(content)) {
      return;
    }
    // the alphabets differ only in + / and - _, so a text that the standard decoder took gives the
    // same bytes with the URL decoder or none at all, and converting them twice would be futile
    tryDecode(Base64.getDecoder(), content)
        .or(() -> tryDecode(Base64.getUrlDecoder(), content))
        .flatMap(decoded -> convertDecoded(decoded, context, rbel))
        .ifPresent(rbel::addFacet);
  }

  private static Optional<RbelContent> tryDecode(Base64.Decoder decoder, RbelContent encoded) {
    try {
      return Optional.of(decode(decoder, encoded));
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }

  // the size of the buffer a stream reads with, which dwarfs the content of most short values
  private static final int SMALL_CONTENT_LIMIT = 8192;

  static RbelContent decode(Base64.Decoder decoder, RbelContent encoded) throws IOException {
    if (encoded.size() <= SMALL_CONTENT_LIMIT) {
      try {
        return RbelContent.wrap(decoder.decode(encoded.toByteArray()));
      } catch (IllegalArgumentException e) {
        // only the stream decoder tolerates a wrong number of padding characters
        if (encoded.get(encoded.size() - 1) != '=') {
          throw e;
        }
      }
    }
    return RbelContent.from(decoder.wrap(encoded.toInputStream()));
  }

  private Optional<RbelBase64Facet> convertDecoded(
      RbelContent decoded, RbelConversionExecutor context, RbelElement parentNode) {
    final RbelElement rawElement =
        RbelElement.builder().content(decoded).parentNode(parentNode).build();
    context.convertElement(rawElement);
    if (rawElement.hasFacet(RbelRootFacet.class)) {
      return Optional.of(new RbelBase64Facet(rawElement));
    }
    return Optional.empty();
  }
}
