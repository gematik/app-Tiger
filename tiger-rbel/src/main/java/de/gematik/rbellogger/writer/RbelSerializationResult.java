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
