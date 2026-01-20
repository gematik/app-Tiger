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
package de.gematik.rbellogger.facets.grpc;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import lombok.Builder;

/**
 * Facet describing a gRPC message with its frames and metadata.
 */
public record RbelGrpcMessageFacet(RbelElement path, RbelElement contentType, RbelElement frames,
                                   RbelElement status, RbelElement statusMessage,
                                   RbelElement trailers) implements RbelFacet {

  /**
   * Create a gRPC message facet.
   *
   * @param path          the RPC path element
   * @param contentType   the content type element
   * @param frames        the list element containing frames
   * @param status        the gRPC status element
   * @param statusMessage the gRPC status message element
   * @param trailers      the trailers element
   */
  @Builder(toBuilder = true)
  public RbelGrpcMessageFacet {
  }

  /**
   * Collect child elements for tree rendering.
   *
   * @return the child element map
   */
  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("path", path)
        .with("contentType", contentType)
        .with("frames", frames)
        .with("status", status)
        .with("statusMessage", statusMessage)
        .with("trailers", trailers);
  }
}
