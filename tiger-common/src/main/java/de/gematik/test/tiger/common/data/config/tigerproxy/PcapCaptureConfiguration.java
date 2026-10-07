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
package de.gematik.test.tiger.common.data.config.tigerproxy;

import static de.gematik.rbellogger.util.MemoryConstants.KB;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How a proxy captures for one capture request. A test suite sets it per proxy under {@code
 * pcapCapture} in the proxy's {@code servers:} entry and sends it with {@code /pcap/admin/start};
 * on the proxy's own configuration it is the fallback.
 */
@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
public class PcapCaptureConfiguration {

  /** Interfaces to capture on: empty is loopback only, {@code ["*"]} the first available one. */
  private List<String> interfaceNames;

  /**
   * Manual BPF filter, used instead of the proxy's automatic port discovery and never widened.
   * Empty (default): the proxy's ports, widened as its routes change.
   */
  private String bpfFilter;

  /**
   * Per-packet bytes copied to userspace, in KB (KB = 1024 B). Default {@code 64}. Values above the
   * native libpcap max (65535 B) are clamped and a single WARN is logged.
   */
  @Builder.Default private int snaplenKb = 64;

  /**
   * Kernel ring buffer size, in KB. Default {@code 16384} (= 16 MB). If packets are observed being
   * dropped, a WARN is logged with the drop count. A remote caller may ask for at most 256 MB of it
   * from a proxy (HTTP 403 beyond that); the proxy's own configuration is not limited.
   */
  @Builder.Default private int bufferSizeKb = 16 * KB;

  /**
   * Policy, read only from a proxy's own configuration: the interfaces a caller may capture on
   * ({@code ["*"]} allows any). Empty: this block's {@link #interfaceNames}, or loopback only.
   * Other interfaces are refused with HTTP 403.
   */
  private List<String> allowedInterfaces;

  /**
   * Policy, read only from a proxy's own configuration: whether a caller may send a {@link
   * #bpfFilter} of its own. Default {@code false}: only one equal to this block's own is accepted.
   */
  private boolean allowCustomFilter;
}
