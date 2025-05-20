/*
 *
 * Copyright 2025 gematik GmbH
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
