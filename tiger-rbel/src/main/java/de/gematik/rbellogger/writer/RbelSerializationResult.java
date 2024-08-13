/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.writer;

import com.google.common.net.MediaType;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RbelSerializationResult {

  private byte[] content;
  @Nullable private RbelContentType contentType;
  private Charset charset;

  public static RbelSerializationResult withUnknownType(byte[] content) {
    return RbelSerializationResult.builder().content(content).contentType(null).build();
  }

  public static RbelSerializationResult of(byte[] content, RbelContentType type, Charset charset) {
    return RbelSerializationResult.builder()
        .content(content)
        .contentType(type)
        .charset(charset)
        .build();
  }

  public static RbelSerializationResult of(RbelContentTreeNode treeRootNode) {
    return RbelSerializationResult.builder()
        .content(treeRootNode.getContent())
        .contentType(treeRootNode.getType())
        .charset(treeRootNode.getElementCharset())
        .build();
  }

  public Optional<RbelContentType> getContentType() {
    return Optional.ofNullable(contentType);
  }

  public Optional<MediaType> getMediaType() {
    return getContentType()
        .map(RbelContentType::getContentTypeString)
        .map(MediaType::parse)
        .map(
            mt -> {
              if (charset != null) {
                return mt.withCharset(charset);
              } else {
                return mt;
              }
            });
  }

  public String getContentAsString() {
    if (getContent() == null) {
      return null;
    }
    if (getCharset() != null) {
      return new String(getContent(), getCharset());
    } else {
      return new String(getContent(), StandardCharsets.UTF_8);
    }
  }
}
