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
package de.gematik.test.tiger.lib;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.*;

/**
 * Configuration for in-process pcap capture (v1: local interface capture).
 *
 * <p>Keys live under {@code lib.pcapCapture.*}. See {@code doc/adr/020_pcap_capture.md}.
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
   * Filename template for one testcase's file; only used when {@link #splitByTestcase} is {@code
   * true}.
   *
   * <p>Resolved by {@code TigerPcapCaptureLifecycle#resolveTestcaseFileName()}: {@code
   * ${scenarioId}} is substituted with the real Tiger scenario id, names longer than 30 characters
   * are truncated with an 8-char UUID-hash suffix to stay unique, and a data-variant suffix is
   * appended for parametrized scenarios. A custom template that drops {@code ${scenarioId}}
   * produces the same filename for every scenario in the run.
   *
   * <p>When {@link #splitByTestcase} is {@code false}, this template is not used; no per-scenario
   * context exists yet at suite start, so the suite-wide file gets a generic, run-unique name
   * ({@code suite_<epochMillis>.pcapng}) instead.
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
  @Builder.Default private int bufferSizeKb = 16 * 1024;

  /**
   * Network interfaces to capture on. Can be:
   *
   * <ul>
   *   <li>{@code null} or empty (default): capture on loopback interface.
   *   <li>Single interface name (e.g. ["eth0"]): capture on that interface.
   *   <li>["*"]: capture on first available interface (preference: non-loopback > loopback).
   *   <li>Multiple names (e.g. ["eth0", "docker0"]): reserved for v2 (multi-interface capture); v1
   *       uses only the first interface and logs a WARN.
   * </ul>
   *
   * BPF port filters are applied regardless of interface selection.
   */
  private List<String> interfaceNames;

  /**
   * Manual BPF filter expression (e.g. {@code "tcp port 8080 or tcp port 9090"}), passed verbatim
   * to {@code PcapHandle#setFilter()}. When set (non-blank), this completely replaces automatic
   * port discovery from running {@code TigerProxyServer} instances, and is never widened or
   * otherwise touched again for the rest of the run — the operator's filter is authoritative.
   *
   * <p>{@code null} or blank (default): use automatic port discovery instead. The resulting filter
   * is widened (never narrowed) as servers appear later in the run — see {@code
   * TigerPcapCaptureLifecycle#receiveTestEnvUpdate} and {@code
   * TigerPcapCaptureService#alsoCapturePorts}.
   */
  private String bpfFilter;

  /**
   * When {@code true}, pcap capture is initialized but starts in suspended state. Use {@link
   * de.gematik.test.tiger.lib.pcap.TigerPcapCaptureService#resume()} to begin capturing. Useful for
   * controlling when packet capture starts within a test scenario.
   */
  @Builder.Default private boolean startSuspended = false;

  /**
   * Reserved for v2 (distributed capture on remote Tiger proxies). Not implemented in v1; must
   * remain {@code false} until v2 ships.
   */
  @Builder.Default private boolean remoteProxies = false;
}
