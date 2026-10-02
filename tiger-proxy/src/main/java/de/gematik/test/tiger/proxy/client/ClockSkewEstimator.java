/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.test.tiger.proxy.controller.TracingpointsController;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import kong.unirest.core.Unirest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Estimates the clock offset between the local machine and a remote Tiger Proxy using an NTP-style
 * approach: multiple round-trips are performed, and the sample with the smallest round-trip time is
 * selected to minimise the impact of network jitter.
 *
 * <p>The returned {@link Duration} represents {@code remoteClock - localClock}. To convert a remote
 * timestamp to local time, <em>subtract</em> this offset.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClockSkewEstimator {

  /**
   * Estimates the clock offset to the remote proxy at {@code remoteBaseUrl}.
   *
   * @param remoteBaseUrl base URL of the remote Tiger Proxy (e.g. {@code http://host:port})
   * @param samples number of round-trips to perform; values &le; 0 disable measurement
   * @return the estimated offset ({@code remoteClock - localClock}), or {@link Optional#empty()} if
   *     measurement was disabled, all samples failed, or the offset came out smaller than the
   *     round-trip can resolve. The caller decides how to handle the empty case (e.g. fall back to
   *     {@link Duration#ZERO}, retry, or fail loudly).
   */
  public static Optional<Duration> estimateOffset(String remoteBaseUrl, int samples) {
    if (samples <= 0) {
      return Optional.empty();
    }

    Duration bestOffset = null;
    Duration bestRtt = null;

    for (int i = 0; i < samples; i++) {
      try {
        Instant localBefore = Instant.now();
        var response =
            Unirest.get(remoteBaseUrl + "/clock")
                .asObject(TracingpointsController.ClockResponse.class);
        Instant localAfter = Instant.now();

        if (!response.isSuccess() || response.getBody() == null) {
          log.atWarn()
              .addArgument(i + 1)
              .addArgument(response::getStatus)
              .addArgument(remoteBaseUrl)
              .log("Clock sync sample {} failed: HTTP {} from {}");
          continue;
        }

        Instant remoteTime = response.getBody().getServerTime().toInstant();
        Duration rtt = Duration.between(localBefore, localAfter);
        Instant localMidpoint = localBefore.plus(rtt.dividedBy(2));
        Duration offset = Duration.between(localMidpoint, remoteTime);

        if (bestRtt == null || rtt.compareTo(bestRtt) < 0) {
          bestRtt = rtt;
          bestOffset = offset;
        }

        log.atTrace()
            .addArgument(i + 1)
            .addArgument(samples)
            .addArgument(rtt::toMillis)
            .addArgument(offset::toMillis)
            .log("Clock sync sample {}/{}: RTT={}ms, offset={}ms");
      } catch (Exception e) {
        log.warn("Clock sync sample {} failed for {}: {}", i + 1, remoteBaseUrl, e.getMessage());
      }
    }

    if (bestOffset == null) {
      log.warn("All {} clock sync samples failed for {}.", samples, remoteBaseUrl);
      return Optional.empty();
    }

    if (isWithinMeasurementError(bestOffset, bestRtt)) {
      log.atInfo()
          .addArgument(remoteBaseUrl)
          .addArgument(bestOffset::toMillis)
          .addArgument(bestRtt::toMillis)
          .log(
              "Clocks of {} and this machine agree to within the measurement error ({}ms offset at"
                  + " {}ms round-trip), leaving timestamps alone");
      return Optional.empty();
    }

    log.atInfo()
        .addArgument(remoteBaseUrl)
        .addArgument(bestOffset::toMillis)
        .addArgument(bestRtt::toMillis)
        .addArgument(samples)
        .log("Estimated clock offset to {}: {}ms (best RTT: {}ms, {} samples)");
    return Optional.of(bestOffset);
  }

  /**
   * An offset smaller than half the round-trip is indistinguishable from no offset at all: the
   * remote timestamp could have been taken anywhere within that window. Reporting it would shift
   * every message by whatever the network happened to do during the measurement.
   */
  static boolean isWithinMeasurementError(Duration offset, Duration roundTrip) {
    return offset.abs().compareTo(roundTrip.dividedBy(2)) < 0;
  }

  /**
   * Adjusts the {@link RbelMessageMetadata#MESSAGE_TRANSMISSION_TIME} of the given element by
   * subtracting the provided clock offset. Does nothing if the offset is zero or no transmission
   * time is present.
   */
  public static void applyCompensation(RbelElement element, Duration offset) {
    if (offset.isZero()) {
      return;
    }
    element
        .getFacet(RbelMessageMetadata.class)
        .ifPresent(metadata -> applyCompensation(metadata, offset));
  }

  /**
   * Adjusts the {@link RbelMessageMetadata#MESSAGE_TRANSMISSION_TIME} on the given metadata
   * instance by subtracting the provided clock offset. Does nothing if the offset is zero or no
   * transmission time is present.
   */
  public static void applyCompensation(RbelMessageMetadata metadata, Duration offset) {
    if (offset.isZero()) {
      return;
    }
    RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME
        .getValue(metadata)
        .ifPresent(remoteTimestamp -> metadata.withTransmissionTime(remoteTimestamp.minus(offset)));
  }

  /**
   * Returns the given metadata values with {@link RbelMessageMetadata#MESSAGE_TRANSMISSION_TIME}
   * moved onto the local timeline, for callers that hold the values before an element exists.
   * Returns the argument unchanged if the offset is zero or no transmission time is present.
   */
  public static Map<String, Object> withCompensatedTransmissionTime(
      Map<String, Object> additionalInformation, Duration offset) {
    val key = RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey();
    if (offset.isZero()
        || !(additionalInformation.get(key) instanceof ZonedDateTime remoteTimestamp)) {
      return additionalInformation;
    }
    val compensated = new HashMap<>(additionalInformation);
    compensated.put(key, remoteTimestamp.minus(offset));
    return compensated;
  }
}
