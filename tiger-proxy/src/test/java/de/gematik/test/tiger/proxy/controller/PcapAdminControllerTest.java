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
package de.gematik.test.tiger.proxy.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.proxy.handler.pcap.PcapCaptureHandler;
import de.gematik.test.tiger.proxy.handler.pcap.PcapCaptureNotAllowedException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("PcapAdminController")
@ExtendWith(MockitoExtension.class)
class PcapAdminControllerTest {

  private PcapAdminController controller;

  @Mock private PcapCaptureHandler pcapCaptureHandler;

  @BeforeEach
  void setUp() {
    controller = new PcapAdminController(pcapCaptureHandler);
  }

  private void authorize() {
    when(pcapCaptureHandler.isKnownCapture("capture-1")).thenReturn(true);
  }

  @Test
  @DisplayName("should start capture successfully")
  void testStartCaptureSuccess() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .capturing(true)
            .filename("test.pcapng")
            .packetCount(0L)
            .fileSizeBytes(0L)
            .build();

    when(pcapCaptureHandler.startCapture("test.pcapng", false, null, null)).thenReturn(response);

    PcapAdminController.StartCaptureRequest request =
        new PcapAdminController.StartCaptureRequest(
            "test.pcapng", "Test scenario", false, null, null);
    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.startCapture(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().isCapturing()).isTrue();
    assertThat(result.getBody().getFilename()).isEqualTo("test.pcapng");
  }

  @Test
  @DisplayName("should hand the capture settings sent with /start to the handler")
  void testStartCapturePassesRequestedConfigToHandler() {
    PcapCaptureConfiguration requested =
        PcapCaptureConfiguration.builder().interfaceNames(List.of("eth0")).build();
    when(pcapCaptureHandler.startCapture("test.pcapng", false, requested, "suite-1"))
        .thenReturn(PcapAdminController.CaptureStatusResponse.builder().capturing(true).build());

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.startCapture(
            new PcapAdminController.StartCaptureRequest(
                "test.pcapng", "Test", false, "suite-1", requested));

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(pcapCaptureHandler).startCapture("test.pcapng", false, requested, "suite-1");
  }

  @Test
  @DisplayName("should answer 403 when the proxy has not opted in to remote capture")
  void testStartCaptureForbiddenWithoutOptIn() {
    when(pcapCaptureHandler.startCapture("test.pcapng", false, null, null))
        .thenThrow(new PcapCaptureNotAllowedException("no pcapCapture block"));

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.startCapture(
            new PcapAdminController.StartCaptureRequest("test.pcapng", "Test", false, null, null));

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(result.getBody().getError()).contains("pcapCapture");
  }

  @Test
  @DisplayName("should discard a file once its download stream is closed")
  void testDownloadDiscardsTheFileWhenDone(@TempDir java.nio.file.Path tempDir) throws Exception {
    java.io.File file =
        java.nio.file.Files.write(tempDir.resolve("s.pcapng"), new byte[] {1, 2, 3}).toFile();
    when(pcapCaptureHandler.getPcapFile("capture-1")).thenReturn(file);
    authorize();

    var result = controller.downloadPcap("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(pcapCaptureHandler, never()).discardPcapFile("capture-1");
    try (java.io.InputStream stream = result.getBody().getInputStream()) {
      assertThat(stream.readAllBytes()).containsExactly(1, 2, 3);
    }
    verify(pcapCaptureHandler).discardPcapFile("capture-1");
  }

  @Test
  @DisplayName("should return conflict if already capturing")
  void testStartCaptureConflict() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .error("Capture already active")
            .capturing(true)
            .build();

    when(pcapCaptureHandler.startCapture("test.pcapng", false, null, null)).thenReturn(response);

    PcapAdminController.StartCaptureRequest request =
        new PcapAdminController.StartCaptureRequest("test.pcapng", "Test", false, null, null);
    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.startCapture(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(result.getBody().getError()).isNotNull();
  }

  @Test
  @DisplayName("should stop capture successfully")
  void testStopCaptureSuccess() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .capturing(false)
            .captureId("capture-1")
            .filename("test.pcapng")
            .packetCount(100L)
            .fileSizeBytes(5000L)
            .downloadUrl("/pcap/admin/download?captureId=capture-1")
            .build();

    when(pcapCaptureHandler.stopCapture("capture-1")).thenReturn(response);
    authorize();

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.stopCapture("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().isCapturing()).isFalse();
    assertThat(result.getBody().getPacketCount()).isEqualTo(100L);
    assertThat(result.getBody().getFileSizeBytes()).isEqualTo(5000L);
  }

  @Test
  @DisplayName("should return bad request if not capturing")
  void testStopCaptureNotActive() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .error("Unknown or already-closed capture: capture-1")
            .capturing(false)
            .build();

    when(pcapCaptureHandler.stopCapture("capture-1")).thenReturn(response);
    authorize();

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.stopCapture("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(result.getBody().getError()).isNotNull();
  }

  @Test
  @DisplayName("should get capture status")
  void testGetCaptureStatus() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .capturing(true)
            .captureId("capture-1")
            .filename("test.pcapng")
            .packetCount(50L)
            .fileSizeBytes(2500L)
            .build();

    when(pcapCaptureHandler.getStatus("capture-1")).thenReturn(response);
    authorize();

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.getCaptureStatus("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().isCapturing()).isTrue();
    assertThat(result.getBody().getPacketCount()).isEqualTo(50L);
  }

  @Test
  @DisplayName("should handle handler exceptions during start")
  void testStartCaptureException() {
    when(pcapCaptureHandler.startCapture("test.pcapng", false, null, null))
        .thenThrow(new RuntimeException("Network error"));

    PcapAdminController.StartCaptureRequest request =
        new PcapAdminController.StartCaptureRequest("test.pcapng", "Test", false, null, null);
    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.startCapture(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getBody().getError()).containsIgnoringCase("Failed to start capture");
  }

  @Test
  @DisplayName("should handle handler exceptions during stop")
  void testStopCaptureException() {
    when(pcapCaptureHandler.stopCapture("capture-1"))
        .thenThrow(new RuntimeException("File system error"));
    authorize();

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.stopCapture("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getBody().getError()).containsIgnoringCase("Failed to stop capture");
  }

  @Test
  @DisplayName("should suspend capture for a capture")
  void testSuspendCaptureSuccess() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .capturing(true)
            .captureId("capture-1")
            .build();

    when(pcapCaptureHandler.suspendCapture("capture-1")).thenReturn(response);
    authorize();

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.suspendCapture("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().getCaptureId()).isEqualTo("capture-1");
  }

  @Test
  @DisplayName("should return bad request suspending an unknown capture")
  void testSuspendCaptureUnknownCapture() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .capturing(false)
            .error("Unknown or already-closed capture: capture-1")
            .build();

    when(pcapCaptureHandler.suspendCapture("capture-1")).thenReturn(response);
    authorize();

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.suspendCapture("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("should answer 500 with the reason when suspend, resume or status fail")
  void failuresOfSuspendResumeAndStatusAreReported() {
    authorize();
    when(pcapCaptureHandler.suspendCapture("capture-1")).thenThrow(new RuntimeException("no"));
    when(pcapCaptureHandler.resumeCapture("capture-1")).thenThrow(new RuntimeException("way"));
    when(pcapCaptureHandler.getStatus("capture-1")).thenThrow(new RuntimeException("out"));

    var suspended = controller.suspendCapture("capture-1");
    var resumed = controller.resumeCapture("capture-1");
    var status = controller.getCaptureStatus("capture-1");

    assertThat(suspended.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(suspended.getBody().getError())
        .startsWith("Failed to suspend capture:")
        .contains("no")
        .contains("\tat ");
    assertThat(resumed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(resumed.getBody().getError())
        .startsWith("Failed to resume capture:")
        .contains("way")
        .contains("\tat ");
    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(status.getBody().getError())
        .startsWith("Failed to get capture status:")
        .contains("out")
        .contains("\tat ");
  }

  @Test
  @DisplayName("should answer 404 for a download whose file is gone, and 500 if it cannot be read")
  void downloadOfAFileThatIsGoneOrUnreadable(@TempDir java.nio.file.Path tempDir) throws Exception {
    authorize();
    when(pcapCaptureHandler.getPcapFile("capture-1"))
        .thenReturn(tempDir.resolve("gone.pcapng").toFile())
        .thenReturn(tempDir.toFile()); // exists, but is not something that can be streamed

    var gone = controller.downloadPcap("capture-1");
    var unreadable = controller.downloadPcap("capture-1");

    assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(unreadable.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(new String(unreadable.getBody().getInputStream().readAllBytes()))
        .startsWith("Failed to download pcap:")
        .contains("\tat ");
    verify(pcapCaptureHandler, never()).discardPcapFile(any());
  }

  @Test
  @DisplayName("should treat an unknown capture as unknown")
  void unknownCapturesAreUnknown() {
    when(pcapCaptureHandler.isKnownCapture("capture-1")).thenReturn(false);

    assertThat(controller.stopCapture("capture-1").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.suspendCapture("capture-1").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.resumeCapture("capture-1").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.getCaptureStatus("capture-1").getBody().isCapturing()).isFalse();
    assertThat(controller.downloadPcap("capture-1").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    verify(pcapCaptureHandler, never()).stopCapture(any());
    verify(pcapCaptureHandler, never()).suspendCapture(any());
    verify(pcapCaptureHandler, never()).resumeCapture(any());
    verify(pcapCaptureHandler, never()).getStatus(any());
    verify(pcapCaptureHandler, never()).getPcapFile(any());
    verify(pcapCaptureHandler, never()).discardPcapFile(any());
  }

  @Test
  @DisplayName("should resume capture for a capture")
  void testResumeCaptureSuccess() {
    PcapAdminController.CaptureStatusResponse response =
        PcapAdminController.CaptureStatusResponse.builder()
            .capturing(true)
            .captureId("capture-1")
            .build();

    when(pcapCaptureHandler.resumeCapture("capture-1")).thenReturn(response);
    authorize();

    ResponseEntity<PcapAdminController.CaptureStatusResponse> result =
        controller.resumeCapture("capture-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().getCaptureId()).isEqualTo("capture-1");
  }
}
