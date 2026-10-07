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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.proxy.handler.pcap.PcapCaptureHandler;
import de.gematik.test.tiger.proxy.handler.pcap.PcapCaptureNotAllowedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import kong.unirest.core.HttpResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(
    name = "Pcap Admin",
    description = "Admin API for distributed pcap capture orchestration (Phase 2)")
@RestController
@RequestMapping("/pcap/admin")
public class PcapAdminController {

  private final PcapCaptureHandler pcapCaptureHandler;

  public PcapAdminController(PcapCaptureHandler pcapCaptureHandler) {
    this.pcapCaptureHandler = pcapCaptureHandler;
  }

  private static ResponseEntity<CaptureStatusResponse> internalServerError(
      String message, Exception cause) {
    log.error(message, cause);
    return CaptureStatusResponse.failed(message, cause)
        .buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @PostMapping("/start")
  @Operation(
      summary = "Start pcap capture",
      description =
          "Instructs the proxy to begin capturing network traffic with the given scenario-scoped filename.")
  @ApiResponse(responseCode = "200", description = "Capture started successfully")
  @ApiResponse(
      responseCode = "403",
      description =
          "This proxy has no pcapCapture block in its configuration, so it does not accept remote"
              + " capture requests")
  @ApiResponse(responseCode = "409", description = "The capture could not be opened")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  public ResponseEntity<CaptureStatusResponse> startCapture(
      @RequestBody StartCaptureRequest request) {
    log.info(
        "Start capture requested, filename={}, gap={}, suiteId={}",
        request.filename,
        request.gap,
        request.suiteId);
    try {
      var result =
          pcapCaptureHandler.startCapture(
              request.filename, request.gap, request.pcapCapture, request.suiteId);
      return result.buildResponseEntity(HttpStatus.CONFLICT);
    } catch (PcapCaptureNotAllowedException e) {
      log.warn("Refused a pcap capture request: {}", e.getMessage());
      return CaptureStatusResponse.builder()
          .error(e.getMessage())
          .build()
          .buildResponseEntity(HttpStatus.FORBIDDEN);
    } catch (RuntimeException e) {
      return internalServerError("Failed to start capture", e);
    }
  }

  @PostMapping("/stop")
  @Operation(
      summary = "Stop pcap capture",
      description =
          "Stops the given pcap capture and returns a download URL for its file (and clock correction metadata). Other concurrently open captures are unaffected.")
  @ApiResponse(responseCode = "200", description = "Capture stopped; download URL included")
  @ApiResponse(responseCode = "400", description = "Capture not currently active")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  public ResponseEntity<CaptureStatusResponse> stopCapture(
      @RequestParam(name = "captureId") String captureId) {
    log.info("Stop capture requested, captureId={}", captureId);
    if (!pcapCaptureHandler.isKnownCapture(captureId)) {
      return CaptureStatusResponse.unknown(captureId).buildResponseEntity();
    }
    try {
      var result = pcapCaptureHandler.stopCapture(captureId);
      return result.buildResponseEntity();
    } catch (RuntimeException e) {
      return internalServerError("Failed to stop capture", e);
    }
  }

  @PostMapping("/suspend")
  @Operation(
      summary = "Suspend pcap capture",
      description =
          "Suspends recording for the given capture; it stays open but writes no more packets until resumed.")
  @ApiResponse(responseCode = "200", description = "Capture suspended")
  @ApiResponse(responseCode = "400", description = "Capture not currently active")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  public ResponseEntity<CaptureStatusResponse> suspendCapture(
      @RequestParam(name = "captureId") String captureId) {
    log.info("Suspend capture requested, captureId={}", captureId);
    if (!pcapCaptureHandler.isKnownCapture(captureId)) {
      return CaptureStatusResponse.unknown(captureId).buildResponseEntity();
    }
    try {
      var result = pcapCaptureHandler.suspendCapture(captureId);
      return result.buildResponseEntity();
    } catch (RuntimeException e) {
      return internalServerError("Failed to suspend capture", e);
    }
  }

  @PostMapping("/resume")
  @Operation(
      summary = "Resume pcap capture",
      description = "Resumes recording for a capture previously suspended via /suspend.")
  @ApiResponse(responseCode = "200", description = "Capture resumed")
  @ApiResponse(responseCode = "400", description = "Capture not currently active")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  public ResponseEntity<CaptureStatusResponse> resumeCapture(
      @RequestParam(name = "captureId") String captureId) {
    log.info("Resume capture requested, captureId={}", captureId);
    if (!pcapCaptureHandler.isKnownCapture(captureId)) {
      return CaptureStatusResponse.unknown(captureId).buildResponseEntity();
    }
    try {
      var result = pcapCaptureHandler.resumeCapture(captureId);
      return result.buildResponseEntity();
    } catch (RuntimeException e) {
      return internalServerError("Failed to resume capture", e);
    }
  }

  @GetMapping("/status")
  @Operation(
      summary = "Query pcap capture status",
      description = "Returns the current state of one pcap capture without changing it.")
  @ApiResponse(responseCode = "200", description = "Current capture status")
  public ResponseEntity<CaptureStatusResponse> getCaptureStatus(
      @RequestParam(name = "captureId") String captureId) {
    if (!pcapCaptureHandler.isKnownCapture(captureId)) {
      return ResponseEntity.ok(CaptureStatusResponse.builder().capturing(false).build());
    }
    try {
      var result = pcapCaptureHandler.getStatus(captureId);
      return ResponseEntity.ok(result);
    } catch (RuntimeException e) {
      return internalServerError("Failed to get capture status", e);
    }
  }

  @GetMapping("/download")
  @Operation(
      summary = "Download pcap file",
      description =
          "Downloads a closed capture's pcap file. Content is streamed without buffering into memory.")
  @ApiResponse(responseCode = "200", description = "Pcap file content (streamed)")
  @ApiResponse(responseCode = "404", description = "Pcap file not found")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  public ResponseEntity<InputStreamResource> downloadPcap(
      @RequestParam(name = "captureId") String captureId) {
    log.info("Download requested for captureId={}", captureId);
    if (!pcapCaptureHandler.isKnownCapture(captureId)) {
      return ResponseEntity.notFound().build();
    }
    try {
      val pcapFile = pcapCaptureHandler.getPcapFile(captureId);
      if (pcapFile == null || !pcapFile.exists()) {
        log.warn("Pcap file not found for captureId: {}", captureId);
        return ResponseEntity.notFound().build();
      }

      InputStream inputStream =
          new FilterInputStream(new BufferedInputStream(new FileInputStream(pcapFile))) {
            @Override
            public void close() throws IOException {
              try {
                super.close();
              } finally {
                pcapCaptureHandler.discardPcapFile(captureId);
              }
            }
          };
      val resource = new InputStreamResource(inputStream);

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + pcapFile.getName() + "\"")
          .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
          .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pcapFile.length()))
          .body(resource);
    } catch (IOException | RuntimeException e) {
      log.error("Failed to download pcap", e);
      byte[] stackTrace =
          CaptureStatusResponse.failed("Failed to download pcap", e)
              .getError()
              .getBytes(StandardCharsets.UTF_8);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.TEXT_PLAIN)
          .body(new InputStreamResource(new ByteArrayInputStream(stackTrace)));
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(onConstructor_ = @JsonIgnore)
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class StartCaptureRequest {
    /** Scenario-scoped filename for the pcap file (e.g., "scenario-001.pcapng"). */
    @JsonProperty("filename")
    private String filename;

    /**
     * Optional metadata (e.g., scenario ID, proxy name) for debugging/audit. Not used in capture
     * logic.
     */
    @JsonProperty("metadata")
    private String metadata;

    /**
     * {@code true} to start a gap/suite-wide capture, which only captures traffic while no ordinary
     * (non-gap) capture of the same {@link #suiteId} is open on this proxy. {@code false} (default)
     * for an ordinary scenario capture.
     */
    @JsonProperty("gap")
    private boolean gap;

    /**
     * Identifies the calling test suite. Optional. A gap capture only yields to ordinary captures
     * of the same client, so several suites can use one proxy without blinding each other's gap
     * files.
     */
    @JsonProperty("suiteId")
    private String suiteId;

    /** How the suite wants this proxy to capture; if absent, the proxy's own settings apply. */
    @JsonProperty("pcapCapture")
    private PcapCaptureConfiguration pcapCapture;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(onConstructor_ = @JsonIgnore)
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class CaptureStatusResponse {
    /** The response for a capture that does not exist (or is not open any more). */
    public static CaptureStatusResponse unknown(String captureId) {
      return builder()
          .capturing(false)
          .error("Unknown or already-closed capture: " + captureId)
          .build();
    }

    /** A response whose error is {@code message} followed by the stack trace of {@code cause}. */
    public static CaptureStatusResponse failed(String message, Throwable cause) {
      StringWriter stackTrace = new StringWriter();
      cause.printStackTrace(new PrintWriter(stackTrace));
      return builder().error(message + ":\n" + stackTrace).build();
    }

    /** Whether the proxy answered successfully and says its capture is running. */
    public static boolean answeredCapturing(HttpResponse<CaptureStatusResponse> response) {
      return response.isSuccess() && response.getBody() != null && response.getBody().isCapturing();
    }

    /** Whether the proxy answered successfully and says its capture is stopped. */
    public static boolean answeredStopped(HttpResponse<CaptureStatusResponse> response) {
      return response.isSuccess()
          && response.getBody() != null
          && !response.getBody().isCapturing();
    }

    /** 200 OK with this response, or 400 Bad Request if it carries an error. */
    public ResponseEntity<CaptureStatusResponse> buildResponseEntity() {
      return buildResponseEntity(HttpStatus.BAD_REQUEST);
    }

    /** 200 OK with this response, or {@code statusOnError} if it carries an error. */
    public ResponseEntity<CaptureStatusResponse> buildResponseEntity(HttpStatus statusOnError) {
      return error == null
          ? ResponseEntity.ok(this)
          : ResponseEntity.status(statusOnError).body(this);
    }

    /** Whether capture is currently active on this proxy. */
    @JsonProperty("capturing")
    private boolean capturing;

    /**
     * Server-generated capture identifier, independent of {@code filename}. Callers must use this
     * (not the filename) to stop/download this specific capture, since filenames are
     * caller-supplied labels that may collide across independent clients.
     */
    @JsonProperty("captureId")
    private String captureId;

    /** Current/last pcap filename (scenario-scoped label, not a unique key). */
    @JsonProperty("filename")
    private String filename;

    /** Timestamp when capture started or will start (ISO-8601 UTC). */
    @JsonProperty("startTime")
    private ZonedDateTime startTime;

    /** Timestamp when capture stopped (on stop response). ISO-8601 UTC. */
    @JsonProperty("stopTime")
    private ZonedDateTime stopTime;

    /** Number of packets captured so far. */
    @JsonProperty("packetCount")
    private long packetCount;

    /** Estimated file size in bytes. */
    @JsonProperty("fileSizeBytes")
    private long fileSizeBytes;

    /**
     * Relative download URL for the pcap file (e.g., "/pcap/admin/download/scenario-001.pcapng").
     * Present only in stop response.
     */
    @JsonProperty("downloadUrl")
    private String downloadUrl;

    /**
     * Error message, if any. Present when capture failed to start or an operation could not be
     * completed.
     */
    @JsonProperty("error")
    private String error;
  }
}
