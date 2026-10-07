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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;

@Slf4j
public final class PcapNetworkInterfaceResolver {

  private PcapNetworkInterfaceResolver() {}

  public interface InterfaceEnumerator {
    List<PcapNetworkInterface> findAllDevs() throws PcapNativeException;
  }

  private static final InterfaceEnumerator DEFAULT_ENUMERATOR = Pcaps::findAllDevs;

  public static Optional<PcapNetworkInterface> resolve(List<String> interfaceNames)
      throws PcapNativeException {
    return resolve(interfaceNames, DEFAULT_ENUMERATOR);
  }

  public static Optional<PcapNetworkInterface> resolve(
      List<String> interfaceNames, InterfaceEnumerator enumerator) throws PcapNativeException {
    List<PcapNetworkInterface> devs = enumerator.findAllDevs();
    if (devs == null || devs.isEmpty()) {
      log.warn("No pcap interfaces found");
      return Optional.empty();
    }

    if (interfaceNames == null || interfaceNames.isEmpty()) {
      return findLoopback(devs);
    }

    if (interfaceNames.size() > 1) {
      log.warn(
          "Multiple interfaces specified ({}): multi-interface capture is reserved for v2; using first interface only",
          interfaceNames);
    }

    String primaryInterface = interfaceNames.get(0);

    if ("*".equals(primaryInterface)) {
      return findAnyInterface(devs);
    }

    return findByName(devs, primaryInterface);
  }

  public static List<PcapNetworkInterface> resolveAll(List<String> interfaceNames)
      throws PcapNativeException {
    return resolveAll(interfaceNames, DEFAULT_ENUMERATOR);
  }

  public static List<PcapNetworkInterface> resolveAll(
      List<String> interfaceNames, InterfaceEnumerator enumerator) throws PcapNativeException {
    List<PcapNetworkInterface> devs = enumerator.findAllDevs();
    if (devs == null || devs.isEmpty()) {
      log.warn("No pcap interfaces found");
      return List.of();
    }

    if (interfaceNames == null || interfaceNames.isEmpty()) {
      return findLoopback(devs).map(List::of).orElse(List.of());
    }

    if (interfaceNames.size() == 1 && "*".equals(interfaceNames.get(0))) {
      return findAnyInterface(devs).map(List::of).orElse(List.of());
    }

    List<PcapNetworkInterface> resolved = new ArrayList<>();
    for (String name : interfaceNames) {
      if (name == null || name.isBlank()) {
        continue;
      }
      Optional<PcapNetworkInterface> found =
          "*".equals(name) ? findAnyInterface(devs) : findByName(devs, name);
      found.ifPresent(resolved::add);
    }
    return resolved;
  }

  private static Optional<PcapNetworkInterface> findLoopback(List<PcapNetworkInterface> devs) {
    return devs.stream().filter(PcapNetworkInterface::isLoopBack).findFirst();
  }

  private static Optional<PcapNetworkInterface> findAnyInterface(List<PcapNetworkInterface> devs) {
    Optional<PcapNetworkInterface> nonLoopback =
        devs.stream().filter(d -> !d.isLoopBack()).findFirst();
    if (nonLoopback.isPresent()) {
      log.debug("Using non-loopback interface: {}", nonLoopback.get().getName());
      return nonLoopback;
    }
    Optional<PcapNetworkInterface> loopback = findLoopback(devs);
    if (loopback.isPresent()) {
      log.debug("No non-loopback interface found, falling back to loopback");
    }
    return loopback;
  }

  private static Optional<PcapNetworkInterface> findByName(
      List<PcapNetworkInterface> devs, String name) {
    Optional<PcapNetworkInterface> found =
        devs.stream().filter(d -> d.getName().equals(name)).findFirst();
    if (found.isEmpty()) {
      log.warn("Interface '{}' not found in available devices: {}", name, devs);
    } else {
      log.debug("Using interface: {}", name);
    }
    return found;
  }
}
