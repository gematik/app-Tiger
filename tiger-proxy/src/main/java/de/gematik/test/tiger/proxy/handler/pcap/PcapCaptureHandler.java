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

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.proxy.controller.PcapAdminController;
import java.io.File;

public interface PcapCaptureHandler {

  PcapAdminController.CaptureStatusResponse startCapture(
      String filename, boolean gap, PcapCaptureConfiguration requestedConfig, String suiteId);

  default PcapAdminController.CaptureStatusResponse startCapture(
      String filename, boolean gap, PcapCaptureConfiguration requestedConfig) {
    return startCapture(filename, gap, requestedConfig, null);
  }

  default PcapAdminController.CaptureStatusResponse startCapture(String filename, boolean gap) {
    return startCapture(filename, gap, null, null);
  }

  boolean isKnownCapture(String captureId);

  PcapAdminController.CaptureStatusResponse stopCapture(String captureId);

  PcapAdminController.CaptureStatusResponse suspendCapture(String captureId);

  PcapAdminController.CaptureStatusResponse resumeCapture(String captureId);

  PcapAdminController.CaptureStatusResponse getStatus(String captureId);

  File getPcapFile(String captureId);

  void discardPcapFile(String captureId);
}
