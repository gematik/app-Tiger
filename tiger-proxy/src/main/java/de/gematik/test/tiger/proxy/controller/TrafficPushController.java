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
package de.gematik.test.tiger.proxy.controller;

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class TrafficPushController {

  public static final String SENDER_REQUEST_HEADER = "tgr-sender";
  public static final String RECEIVER_REQUEST_HEADER = "tgr-receiver";
  public static final String TIMESTAMP_REQUEST_HEADER = "tgr-timestamp";

  private final TigerProxy tigerProxy;

  @PostMapping(value = "/traffic")
  public void postNewMessage(
      InputStream dataStream,
      @RequestHeader(SENDER_REQUEST_HEADER) final Optional<String> sender,
      @RequestHeader(RECEIVER_REQUEST_HEADER) final Optional<String> receiver,
      @RequestHeader(TIMESTAMP_REQUEST_HEADER) final Optional<String> timestamp)
      throws IOException {
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .parseMessage(
            IOUtils.toByteArray(dataStream),
            new RbelMessageMetadata()
                .withSender(sender.flatMap(RbelHostname::fromString).orElse(null))
                .withReceiver(receiver.flatMap(RbelHostname::fromString).orElse(null))
                .withTransmissionTime(timestamp.map(ZonedDateTime::parse).orElse(null)));
  }
}
