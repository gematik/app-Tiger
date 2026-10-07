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
package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.lib.pcap.RemotePcapCoordinator;
import de.gematik.test.tiger.lib.pcap.ScenarioPcapCaptureService;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;

/** Steps to suspend and resume the local capture and this scenario's remote proxy captures. */
@Slf4j
public class PcapCaptureControlStepDefs {

  @When("PCAP capture is suspended")
  public void suspendPcapCapture() {
    ScenarioPcapCaptureService service = ScenarioPcapCaptureService.getInstance();
    if (service != null) {
      service.suspend();
      log.info("Pcap capture suspended (local)");
    }

    RemotePcapCoordinator coordinator = RemotePcapCoordinator.getInstance();
    if (coordinator != null) {
      coordinator.suspend();
      log.info("Pcap capture suspended (remote)");
    }

    if (service == null && coordinator == null) {
      log.warn("Pcap capture not initialized; skipping suspend");
    }
  }

  @When("PCAP capture resumes")
  public void resumePcapCapture() {
    ScenarioPcapCaptureService service = ScenarioPcapCaptureService.getInstance();
    if (service != null) {
      service.resume();
      log.info("Pcap capture resumed (local)");
    }

    RemotePcapCoordinator coordinator = RemotePcapCoordinator.getInstance();
    if (coordinator != null) {
      coordinator.resume();
      log.info("Pcap capture resumed (remote)");
    }

    if (service == null && coordinator == null) {
      log.warn("Pcap capture not initialized; skipping resume");
    }
  }
}
