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

import de.gematik.test.tiger.testutils.pcap.FakePcapNetwork;
import java.util.List;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;

class FakeNetworkFanOutService extends FanOutPcapCaptureService {

  private final FakePcapNetwork network;

  FakeNetworkFanOutService(FakePcapNetwork network) {
    this.network = network;
  }

  FakeNetworkFanOutService(FakePcapNetwork network, int snaplenKb, int bufferSizeKb) {
    super(snaplenKb, bufferSizeKb);
    this.network = network;
  }

  @Override
  protected List<PcapNetworkInterface> resolveInterfaces(List<String> names) {
    return network.resolve(names);
  }

  @Override
  protected PcapHandle openHandle(PcapNetworkInterface iface, int snaplenBytes, int bufferBytes)
      throws PcapNativeException {
    return network.open(iface, snaplenBytes, bufferBytes);
  }
}
