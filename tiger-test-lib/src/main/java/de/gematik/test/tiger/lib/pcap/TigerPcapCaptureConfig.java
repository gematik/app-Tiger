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
package de.gematik.test.tiger.lib.pcap;

import static de.gematik.rbellogger.util.MemoryConstants.KB;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.*;

/**
 * Configuration for in-process pcap capture (v1: local loopback only).
 *
 * <p>Keys live under {@code lib.pcapCapture.*}. See {@code doc/adr/020_pcap_capture.md} and {@code
 * doc/planning/pcap-capture-v1-plan.md}.
 */
@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@ToString
@JsonInclude(Include.NON_NULL)
public class TigerPcapCaptureConfig {

  /** Master switch. When false, no capture threads are started, no files are written. */
  @Builder.Default private boolean enabled = false;

  /**
   * Filename template of the {@code .pcapng} files; {@code ${scenarioId}} is replaced per scenario.
   */
  @Builder.Default private String filename = "${scenarioId}.pcapng";

  /**
   * One pcap file per Cucumber scenario ({@code true}, default) vs one file for the entire suite
   * ({@code false}).
   */
  @Builder.Default private boolean splitByTestcase = true;

  /**
   * Per-packet bytes copied to userspace, in KB (KB = 1024 B). Default {@code 64} covers full
   * loopback frames (Linux loopback MTU 65536, macOS 16384). Values that resolve above the native
   * libpcap max (65535 B) are clamped and a single WARN is logged.
   */
  @Builder.Default private int snaplenKb = 64;

  /**
   * Kernel ring buffer size, in KB. Default {@code 16384} (= 16 MB) absorbs bursts across JVM/GC
   * pauses. If {@code PcapHandle#getStats().psDrop > 0} is observed at rotation time, a WARN is
   * logged with the drop count so silent drops are visible.
   */
  @Builder.Default private int bufferSizeKb = 16 * KB;

  /**
   * Interfaces to capture on at once. Empty (default): loopback; {@code ["*"]}: the first available
   * interface, non-loopback preferred; otherwise the names given. The traffic of all interfaces is
   * merged by timestamp into one file per scenario.
   */
  private List<String> interfaceNames;

  /**
   * Manual BPF filter, used instead of the automatic port discovery and never widened. Empty
   * (default): the ports of the running proxies, widened as servers appear.
   */
  private String bpfFilter;

  /**
   * When {@code true}, pcap capture is initialized but starts in suspended state. Use {@link
   * de.gematik.test.tiger.lib.pcap.ScenarioPcapCaptureService#resume()} to begin capturing. Useful
   * for controlling when packet capture starts within a test scenario.
   */
  @Builder.Default private boolean startSuspended = false;

  /** Also capture on the remote Tiger proxies, started and stopped around each scenario. */
  @Builder.Default private boolean remoteProxies = false;

  /**
   * Merge the remote captures, clock-corrected, into the scenario's file; needs {@code
   * remoteProxies}.
   */
  @Builder.Default private boolean mergeRemotePcaps = true;

  /**
   * Drop packets captured twice (by two interfaces, or by this capture and a proxy on the same
   * machine) when files are merged. Default {@code false}: they are only reported. Duplicates are
   * identical bytes from two different sources within a few milliseconds; traffic seen through NAT
   * is never recognized.
   */
  @Builder.Default private boolean dropDuplicatePackets = false;

  /**
   * Timeout in seconds for every call to a remote proxy — starting, stopping, suspending and
   * resuming a capture, and each wait for data while downloading its file. Default {@code 30}.
   * Increase for high-latency networks; without it a proxy that stops answering would hang the
   * scenario that is waiting for it.
   */
  @Builder.Default private int remoteDownloadTimeoutSeconds = 30;
}
