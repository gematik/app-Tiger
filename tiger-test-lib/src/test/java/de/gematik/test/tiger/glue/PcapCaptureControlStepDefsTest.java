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

import static org.mockito.Mockito.*;

import de.gematik.test.tiger.lib.pcap.TigerPcapCaptureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PcapCaptureControlStepDefs")
class PcapCaptureControlStepDefsTest {

  private PcapCaptureControlStepDefs stepDefs;
  private TigerPcapCaptureService mockService;

  @BeforeEach
  void setUp() {
    stepDefs = new PcapCaptureControlStepDefs();
    mockService = mock(TigerPcapCaptureService.class);
    TigerPcapCaptureService.setInstance(mockService);
  }

  @AfterEach
  void tearDown() {
    TigerPcapCaptureService.setInstance(null);
  }

  @Test
  @DisplayName("Suspend step calls service.suspend()")
  void suspendCallsServiceSuspend() {
    stepDefs.suspendPcapCapture();
    verify(mockService).suspend();
  }

  @Test
  @DisplayName("Resume step calls service.resume()")
  void resumeCallsServiceResume() {
    stepDefs.resumePcapCapture();
    verify(mockService).resume();
  }

  @Test
  @DisplayName("Suspend handles null service gracefully")
  void suspendHandlesNullService() {
    TigerPcapCaptureService.setInstance(null);
    stepDefs.suspendPcapCapture(); // Should not throw
  }

  @Test
  @DisplayName("Resume handles null service gracefully")
  void resumeHandlesNullService() {
    TigerPcapCaptureService.setInstance(null);
    stepDefs.resumePcapCapture(); // Should not throw
  }
}
