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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;

@DisplayName("PcapNetworkInterfaceResolver")
class PcapNetworkInterfaceResolverTest {

  @Mock private PcapNetworkInterface mockLoopback;
  @Mock private PcapNetworkInterface mockEth0;
  @Mock private PcapNetworkInterface mockEth1;
  @Mock private PcapNetworkInterfaceResolver.InterfaceEnumerator mockEnumerator;

  PcapNetworkInterfaceResolverTest() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("Null interface names returns loopback")
  void nullInterfaceNamesReturnsLoopback() throws PcapNativeException {
    when(mockLoopback.isLoopBack()).thenReturn(true);
    when(mockEth0.isLoopBack()).thenReturn(false);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockLoopback));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(null, mockEnumerator);

    assertThat(result).contains(mockLoopback);
  }

  @Test
  @DisplayName("Empty interface names returns loopback")
  void emptyInterfaceNamesReturnsLoopback() throws PcapNativeException {
    when(mockLoopback.isLoopBack()).thenReturn(true);
    when(mockEth0.isLoopBack()).thenReturn(false);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockLoopback));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of(), mockEnumerator);

    assertThat(result).contains(mockLoopback);
  }

  @Test
  @DisplayName("Specific interface name finds that interface")
  void specificInterfaceNameFindsIt() throws PcapNativeException {
    when(mockEth0.getName()).thenReturn("eth0");
    when(mockEth0.isLoopBack()).thenReturn(false);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockLoopback));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("eth0"), mockEnumerator);

    assertThat(result).contains(mockEth0);
  }

  @Test
  @DisplayName("Wildcard '*' returns non-loopback if available")
  void wildcardReturnsNonLoopback() throws PcapNativeException {
    when(mockEth0.isLoopBack()).thenReturn(false);
    when(mockLoopback.isLoopBack()).thenReturn(true);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockLoopback));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("*"), mockEnumerator);

    assertThat(result).contains(mockEth0);
  }

  @Test
  @DisplayName("Wildcard '*' falls back to loopback when no non-loopback available")
  void wildcardFallsBackToLoopback() throws PcapNativeException {
    when(mockLoopback.isLoopBack()).thenReturn(true);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockLoopback));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("*"), mockEnumerator);

    assertThat(result).contains(mockLoopback);
  }

  @Test
  @DisplayName("Non-existent interface name returns empty")
  void nonExistentInterfaceReturnsEmpty() throws PcapNativeException {
    when(mockEth0.getName()).thenReturn("eth0");
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("eth1"), mockEnumerator);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Empty device list returns empty")
  void emptyDeviceListReturnsEmpty() throws PcapNativeException {
    when(mockEnumerator.findAllDevs()).thenReturn(List.of());

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("eth0"), mockEnumerator);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Null device list returns empty")
  void nullDeviceListReturnsEmpty() throws PcapNativeException {
    when(mockEnumerator.findAllDevs()).thenReturn(null);

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("eth0"), mockEnumerator);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Multiple interface names uses first and logs warning")
  void multipleInterfaceNamesUsesFirst() throws PcapNativeException {
    when(mockEth0.getName()).thenReturn("eth0");
    when(mockEth1.getName()).thenReturn("eth1");
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockEth1));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("eth0", "eth1"), mockEnumerator);

    assertThat(result).contains(mockEth0);
  }

  @Test
  @DisplayName("PcapNativeException from enumerator propagates")
  void pcapNativeExceptionPropagates() throws PcapNativeException {
    when(mockEnumerator.findAllDevs()).thenThrow(new PcapNativeException("Test error"));

    assertThatThrownBy(() -> PcapNetworkInterfaceResolver.resolve(List.of("eth0"), mockEnumerator))
        .isInstanceOf(PcapNativeException.class)
        .hasMessage("Test error");
  }

  @Test
  @DisplayName("Loopback is not found when looking for specific non-loopback")
  void loopbackNotFoundWhenLookingForNonLoopback() throws PcapNativeException {
    when(mockLoopback.getName()).thenReturn("lo");
    when(mockLoopback.isLoopBack()).thenReturn(true);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockLoopback));

    Optional<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolve(List.of("eth0"), mockEnumerator);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("resolveAll with multiple names resolves every one of them")
  void resolveAllMultipleNamesResolvesEveryOne() throws PcapNativeException {
    when(mockEth0.getName()).thenReturn("eth0");
    when(mockEth1.getName()).thenReturn("eth1");
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockEth1, mockLoopback));

    List<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolveAll(List.of("eth0", "eth1"), mockEnumerator);

    assertThat(result).containsExactly(mockEth0, mockEth1);
  }

  @Test
  @DisplayName("resolveAll skips a name that doesn't resolve, keeping the rest")
  void resolveAllSkipsUnresolvableNameGracefully() throws PcapNativeException {
    when(mockEth0.getName()).thenReturn("eth0");
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0));

    List<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolveAll(List.of("eth0", "does-not-exist"), mockEnumerator);

    assertThat(result).containsExactly(mockEth0);
  }

  @Test
  @DisplayName("resolveAll with three interfaces (arbitrary N) resolves all three")
  void resolveAllArbitraryCountResolvesAll() throws PcapNativeException {
    PcapNetworkInterface mockEth2 = mock(PcapNetworkInterface.class);
    when(mockEth0.getName()).thenReturn("eth0");
    when(mockEth1.getName()).thenReturn("eth1");
    when(mockEth2.getName()).thenReturn("eth2");
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockEth1, mockEth2));

    List<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolveAll(List.of("eth0", "eth1", "eth2"), mockEnumerator);

    assertThat(result).containsExactly(mockEth0, mockEth1, mockEth2);
  }

  @Test
  @DisplayName("resolveAll with null/empty names resolves to just the loopback")
  void resolveAllDefaultsToLoopback() throws PcapNativeException {
    when(mockLoopback.isLoopBack()).thenReturn(true);
    when(mockEth0.isLoopBack()).thenReturn(false);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockLoopback));

    assertThat(PcapNetworkInterfaceResolver.resolveAll(null, mockEnumerator))
        .containsExactly(mockLoopback);
    assertThat(PcapNetworkInterfaceResolver.resolveAll(List.of(), mockEnumerator))
        .containsExactly(mockLoopback);
  }

  @Test
  @DisplayName("resolveAll with no devices returns an empty list")
  void resolveAllNoDevicesReturnsEmptyList() throws PcapNativeException {
    when(mockEnumerator.findAllDevs()).thenReturn(List.of());

    assertThat(PcapNetworkInterfaceResolver.resolveAll(List.of("eth0", "eth1"), mockEnumerator))
        .isEmpty();
  }

  @Test
  @DisplayName("resolveAll with wildcard '*' resolves to a single interface")
  void resolveAllWildcardResolvesToSingleInterface() throws PcapNativeException {
    when(mockEth0.isLoopBack()).thenReturn(false);
    when(mockLoopback.isLoopBack()).thenReturn(true);
    when(mockEnumerator.findAllDevs()).thenReturn(List.of(mockEth0, mockLoopback));

    List<PcapNetworkInterface> result =
        PcapNetworkInterfaceResolver.resolveAll(List.of("*"), mockEnumerator);

    assertThat(result).containsExactly(mockEth0);
  }
}
