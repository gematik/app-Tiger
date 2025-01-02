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

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.ProxyTransmissionHistory;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerTracingDto {

  private final String requestUuid;
  private final String responseUuid;
  private final RbelHostname sender;
  private final RbelHostname receiver;
  private final ZonedDateTime requestTransmissionTime;
  private final ZonedDateTime responseTransmissionTime;
  private final Map<String, String> additionalInformationRequest;
  private final Map<String, String> additionalInformationResponse;
  private final Long sequenceNumberRequest;
  private final Long sequenceNumberResponse;
  private final ProxyTransmissionHistory proxyTransmissionHistoryRequest;
  private final ProxyTransmissionHistory proxyTransmissionHistoryResponse;
  @Builder.Default private final boolean unparsedChunk = false;
}
