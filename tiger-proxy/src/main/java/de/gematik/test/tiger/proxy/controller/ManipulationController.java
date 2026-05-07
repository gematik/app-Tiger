/*
 *
 * Copyright 2021-2025 gematik GmbH
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

import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.controller.dto.JwtManipulationRequest;
import de.gematik.test.tiger.proxy.data.JwtManipulationConfiguration;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Data
@RequiredArgsConstructor
@Validated
@RestController
@Slf4j
@RequestMapping("/manipulation")
public class ManipulationController {
  private final TigerProxy tigerProxy;

  @PostMapping(value = "/modifyJwt", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> modifyJwt(@RequestBody JwtManipulationRequest request) {
    // Validate required fields
    if (StringUtils.isBlank(request.getJwtLocation())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jwtLocation must not be empty");
    }
    if (StringUtils.isBlank(request.getJwtField())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jwtField must not be empty");
    }
    if (request.getReplaceWith() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "replaceWith must not be null");
    }

    // Keep JWT manipulation state on the proxy instance so concurrent proxies do not share static
    // callback state or execution counters.
    tigerProxy.configureJwtManipulation(
        JwtManipulationConfiguration.builder()
            .name(request.getName())
            .condition(request.getCondition())
            .jwtLocation(request.getJwtLocation())
            .jwtField(request.getJwtField())
            .replaceWith(request.getReplaceWith())
            .privateKeyPem(request.getPrivateKeyPem())
            .deleteAfterNExecutions(request.getDeleteAfterNExecutions())
            .replaceJwk(request.getReplaceJwk())
            .dpopLocation(request.getDpopLocation())
            .dpopPrivateKeyPem(request.getDpopPrivateKeyPem())
            .updateAth(request.getUpdateAth())
            .build());

    log.info("JWT manipulation configured: {} {} -> {}", request.getJwtLocation(), request.getJwtField(), request.getReplaceWith());

    String response = "JWT manipulation applied: " + request.getJwtLocation() + " " + request.getJwtField();
    if (StringUtils.isNotBlank(request.getPrivateKeyPem())) {
      response += " (with re-signing)";
    } else {
      response += " (no re-signing)";
    }

    return ResponseEntity.ok(response);
  }

  /**
   * Revert all JWT manipulations.
   *
   * @return ok if the manipulations were removed
   */
  @PostMapping(value = "/resetJwtManipulation")
  public ResponseEntity<String> resetManipulation() {
    // Reset only the JWT-specific state. RBEL modifications can be inspected or cleared
    // independently via the dedicated manipulation endpoints.
    tigerProxy.clearJwtManipulation();
    return ResponseEntity.ok("JWT manipulation deactivated");
  }

  /**
   * Get active JWT manipulations.
   *
   * @return active manipulation as string
   */
  @GetMapping(value = "/getJwtManipulation")
  public ResponseEntity<String> getManipulation() {
    var activeManipulations = new ArrayList<String>();
    tigerProxy
        .getJwtManipulationConfiguration()
        .ifPresent(
            configuration ->
                activeManipulations.add(
                    "Active JWT manipulation: "
                        + configuration.getJwtLocation()
                        + " "
                        + configuration.getJwtField()
                        + " = "
                        + configuration.getReplaceWith()));
    var modifications = tigerProxy.getModifications();
    if (!modifications.isEmpty()) {
      activeManipulations.add("Active RBel modifications: " + modifications.size());
    }

    if (activeManipulations.isEmpty()) {
      return ResponseEntity.ok("No active manipulation");
    }
    return ResponseEntity.ok(String.join("; ", activeManipulations));
  }

  /**
   * Delete of all existing manipulations.
   *
   * @return Ok with everything worked correctly
   */
  @DeleteMapping(value = "/modification")
  public ResponseEntity<String> deleteAllModification() {
    // This endpoint mirrors the previous "reset everything" behavior for callers that expect both
    // JWT manipulation and classic RBEL modifications to be cleared together.
    tigerProxy.clearAllManipulations();
    return ResponseEntity.ok("All manipulation are deactivated");
  }

  /**
   * Add a key-file to tigerproxy for resign after manipulation.
   * Accepts JSON with algorithm and base64-encoded key.
   * Request format:
   * {
   *   "algorithm": "EC",
   *   "keyBase64": "MIGTAgEAMBMGByqGSM..."
   * }
   *
   * @param name Name of the key, needed for TigerProxy to find the right key for a manipulation.
   */
  @PutMapping(value = "/key/{name}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public void addKey(
      @PathVariable("name") String name,
      @RequestBody KeyRequest request
  ) throws Exception {
    // Clean base64 input
    String sanitizedKey = request.keyBase64().replaceAll("[\\r\\n\\s]+", "");

    // Decode Base64 to DER bytes
    byte[] keyBytes = Base64.getDecoder().decode(sanitizedKey);

    // Create PrivateKey with specified algorithm
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance(request.algorithm());
    PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

    tigerProxy.addKey(name, privateKey);
  }

  /**
   * JSON Request DTO with algorithm and base64 key
   */
  public record KeyRequest(String algorithm, String keyBase64) {}

}
