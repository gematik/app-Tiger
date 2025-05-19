package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class TcpConnectionEntry {
  String uuid;
  List<String> sourceUuids = new ArrayList<>();
  RbelContent data;
  TcpIpConnectionIdentifier connectionIdentifier;
  Map<String, Object> additionalData = new HashMap<>();
  Consumer<RbelElement> messagePreProcessor;
  Long sequenceNumber;
  String previousUuid;
  Integer positionInBaseNode;

  public static TcpConnectionEntry empty() {
    return new TcpConnectionEntry(
        null, RbelContent.builder().build(), null, msg -> {}, null, null, null);
  }

  public TcpConnectionEntry addSourceUuids(ArrayList<String> sourceUuids) {
    if (sourceUuids != null) {
      this.sourceUuids.addAll(sourceUuids);
    }
    return this;
  }

  public TcpConnectionEntry addAdditionalData(String key, Object value) {
    this.additionalData.put(key, value);
    return this;
  }

  public TcpConnectionEntry addAdditionalData(Map<String, Object> additionalData) {
    if (additionalData != null) {
      this.additionalData.putAll(additionalData);
    }
    return this;
  }
}
