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
package de.gematik.test.tiger.proxy.handler.pcap;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public final class BpfFilterBuilder {

  private BpfFilterBuilder() {}

  public static String forTcpPorts(Collection<Integer> ports) {
    if (ports == null || ports.isEmpty()) {
      return "";
    }
    var valid =
        ports.stream()
            .filter(Objects::nonNull)
            .filter(port -> port > 0 && port <= 65535)
            .distinct()
            .sorted()
            .toList();
    if (valid.isEmpty()) {
      return "";
    }
    return "tcp port "
        + valid.stream().map(String::valueOf).collect(Collectors.joining(" or tcp port "));
  }
}
