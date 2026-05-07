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
package de.gematik.test.tiger.proxy.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.JwtManipulationConfiguration;
import java.io.StringReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jose4j.jwk.EllipticCurveJsonWebKey;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jws.EcdsaUsingShaAlgorithm;

/**
 * Utility for request-scoped JWT manipulation using proxy-scoped configuration.
 *
 * <p>The configuration separates where the JWT is located, for example
 * {@code $.header.dpop} or {@code $.body.client_assertion}, from what should change inside the
 * JWT, for example {@code header.typ}, {@code body.iss}, or {@code body.aud.0}.
 */
@Slf4j
public final class JwtManipulationCallback {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Pattern JWT_HEADER_PREFIX_PATTERN =
      Pattern.compile("^(dpop|bearer)(\\s+)(.+)$", Pattern.CASE_INSENSITIVE);

  private final TigerProxy tigerProxy;
  private final JwtManipulationConfiguration configuration;

  /** Create a request-scoped helper for one immutable manipulation configuration snapshot. */
  private JwtManipulationCallback(
      TigerProxy tigerProxy, JwtManipulationConfiguration configuration) {
    this.tigerProxy = tigerProxy;
    this.configuration = configuration;
  }

  /**
   * Build a request-scoped helper from the proxy's current configuration so concurrent proxies do
   * not share static manipulation state.
   */
  public static void applyJwtManipulationIfConfigured(TigerProxy tigerProxy, HttpRequest request) {
    tigerProxy
        .getJwtManipulationConfiguration()
        .ifPresent(
            configuration ->
                new JwtManipulationCallback(tigerProxy, configuration).applyJwtManipulation(request));
  }

  /**
   * Applies the configured JWT manipulation to the given request, including optional request
   * filtering, token re-signing, DPoP {@code ath} recalculation, and execution-limit handling.
   */
  public void applyJwtManipulation(HttpRequest request) {
    if (configuration.getJwtLocation() == null
        || configuration.getJwtField() == null
        || configuration.getReplaceWith() == null) {
      return;
    }

    try {
      // Apply the optional request filter before touching the JWT.
      if (configuration.getCondition() != null
          && !configuration.getCondition().isBlank()
          && !matchesCondition(request)) {
        log.debug("Condition not matched for request");
        return;
      }

      // jwtLocation identifies the HTTP container that holds the JWT, for example
      // $.header.authorization or $.body.client_assertion.
      String[] locationParts = configuration.getJwtLocation().split("\\.");
      if (locationParts.length < 3 || !"$".equals(locationParts[0])) {
        log.warn("Invalid jwtLocation format: {}", configuration.getJwtLocation());
        return;
      }

      String httpPart = locationParts[1];
      String containerName = normalizeLocationSegment(locationParts[2]);

      // jwtField identifies the JWT section plus the field path inside that section, for example
      // body.client_statement.platform or body.aud.0.
      String[] fieldParts = configuration.getJwtField().split("\\.", 2);
      if (fieldParts.length < 2) {
        log.warn("Invalid jwtField format: {}", configuration.getJwtField());
        return;
      }

      String jwtPart = fieldParts[0];
      String fieldPath = fieldParts[1];

      // Treat a numeric last segment as an array index so paths like aud.0 can target a single
      // element while the remaining prefix still addresses the array field itself.
      Integer arrayIndex = null;
      String fieldName = fieldPath;
      String[] pathParts = fieldPath.split("\\.");
      if (pathParts.length >= 2) {
        String lastPart = pathParts[pathParts.length - 1];
        try {
          arrayIndex = Integer.parseInt(lastPart);
          fieldName = fieldPath.substring(0, fieldPath.lastIndexOf('.'));
        } catch (NumberFormatException e) {
          fieldName = fieldPath;
        }
      }

      String manipulatedJwt = null;
      if ("header".equals(httpPart)) {
        manipulatedJwt = manipulateHeaderJwt(request, containerName, jwtPart, fieldName, arrayIndex);
      } else if ("body".equals(httpPart)) {
        manipulateBodyJwt(request, containerName, jwtPart, fieldName, arrayIndex);
      }

      log.info(
          "JWT manipulation applied: {} {} = {}",
          configuration.getJwtLocation(),
          configuration.getJwtField(),
          configuration.getReplaceWith());

      // Manipulating the access token invalidates the DPoP ath claim, so recalculate it when the
      // request also carries a DPoP proof.
      if (manipulatedJwt != null && "authorization".equalsIgnoreCase(containerName)) {
        updateDpopAth(request, manipulatedJwt);
      }

      // deleteAfterNExecutions is tracked on the proxy instance so separate proxies do not share
      // execution counters.
      if (configuration.getDeleteAfterNExecutions() != null
          && tigerProxy.recordJwtManipulationExecutionAndClearIfNeeded()) {
        log.info(
            "JWT manipulation reached configured execution limit {}, clearing configuration",
            configuration.getDeleteAfterNExecutions());
      }
    } catch (Exception e) {
      log.error("Failed to apply JWT manipulation: {}", e.getMessage(), e);
    }
  }

  /**
   * Supports path regexes, body-field equality/regex checks, and simple {@code &&}/{@code ||}
   * combinations.
   */
  private boolean matchesCondition(HttpRequest request) {
    String conditionStr = configuration.getCondition().trim();

    if (conditionStr.contains("&&")) {
      String[] parts = splitConditionOperator(conditionStr, "&&");
      for (String part : parts) {
        if (!evaluateSingleCondition(request, part.trim())) {
          return false;
        }
      }
      return true;
    }

    if (conditionStr.contains("||")) {
      String[] parts = splitConditionOperator(conditionStr, "||");
      for (String part : parts) {
        if (evaluateSingleCondition(request, part.trim())) {
          return true;
        }
      }
      return false;
    }

    return evaluateSingleCondition(request, conditionStr);
  }

  /** Evaluate one non-composite condition against the current request. */
  private static boolean evaluateSingleCondition(HttpRequest request, String conditionStr) {
    try {
      if (conditionStr.contains("message.path") && conditionStr.contains("=~")) {
        String pattern = extractQuotedValue(conditionStr.substring(conditionStr.indexOf("=~") + 2));
        String requestPath = request.getPath() != null ? request.getPath() : "";
        boolean matches = requestPath.matches(pattern);
        log.debug(
            "Path condition: pattern='{}', requestPath='{}', matches={}",
            pattern,
            requestPath,
            matches);
        return matches;
      }

      if (conditionStr.contains("message.body.")
          && (conditionStr.contains("==") || conditionStr.contains("=~"))) {
        int bodyIdx = conditionStr.indexOf("message.body.") + "message.body.".length();
        boolean isRegex = conditionStr.contains("=~");
        String operator = isRegex ? "=~" : "==";
        int opIdx = conditionStr.indexOf(operator);
        String paramName = conditionStr.substring(bodyIdx, opIdx).trim();
        String expectedValue =
            extractQuotedValue(conditionStr.substring(opIdx + operator.length()));

        String bodyStr = getBodyAsString(request);
        if (bodyStr == null) {
          log.debug("Body condition: paramName='{}', body is null, matches=false", paramName);
          return false;
        }

        String actualValue = extractFormParameter(bodyStr, paramName);
        if (actualValue != null) {
          boolean matches =
              isRegex ? actualValue.matches(expectedValue) : actualValue.equals(expectedValue);
          log.debug(
              "Body condition (form): paramName='{}', expectedValue='{}', actualValue='{}', isRegex={}, matches={}",
              paramName,
              expectedValue,
              actualValue,
              isRegex,
              matches);
          return matches;
        }

        try {
          JsonNode bodyNode = mapper.readTree(bodyStr);
          if (bodyNode.has(paramName)) {
            String jsonValue = bodyNode.get(paramName).asText();
            boolean matches =
                isRegex ? jsonValue.matches(expectedValue) : jsonValue.equals(expectedValue);
            log.debug(
                "Body condition (json): paramName='{}', expectedValue='{}', actualValue='{}', isRegex={}, matches={}",
                paramName,
                expectedValue,
                jsonValue,
                isRegex,
                matches);
            return matches;
          }
        } catch (Exception e) {
          log.debug("Body is not JSON, parameter {} not found", paramName);
        }

        log.debug("Body condition: paramName='{}' not found in body", paramName);
        return false;
      }

      String pattern = extractQuotedValue(conditionStr);
      String requestPath = request.getPath() != null ? request.getPath() : "";
      try {
        return requestPath.matches(pattern);
      } catch (PatternSyntaxException e) {
        log.warn("Invalid regex pattern in condition: {}", pattern, e);
        return false;
      }
    } catch (Exception e) {
      log.warn("Failed to evaluate condition: {}", conditionStr, e);
      return false;
    }
  }

  /** Remove optional surrounding single or double quotes from a condition fragment. */
  private static String extractQuotedValue(String str) {
    String trimmed = str.trim();
    if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
        || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  /**
   * Normalize one location segment from {@code $.header.foo} or bracketed variants such as
   * {@code $.header.[~'DPoP']} to the raw header/body field name.
   */
  private static String normalizeLocationSegment(String segment) {
    String normalized = segment.trim();
    boolean bracketedSegment = false;
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      bracketedSegment = true;
      normalized = normalized.substring(1, normalized.length() - 1).trim();
    }
    if (bracketedSegment && normalized.startsWith("~")) {
      normalized = normalized.substring(1).trim();
    }
    if ((normalized.startsWith("'") && normalized.endsWith("'"))
        || (normalized.startsWith("\"") && normalized.endsWith("\""))) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    return normalized;
  }

  /** Read the request body as UTF-8 text when present. */
  private static String getBodyAsString(HttpRequest request) {
    byte[] bodyBytes = request.getBody();
    if (bodyBytes == null || bodyBytes.length == 0) {
      return null;
    }
    return new String(bodyBytes, StandardCharsets.UTF_8);
  }

  /** Extract one form parameter from an {@code application/x-www-form-urlencoded} body. */
  private static String extractFormParameter(String body, String paramName) {
    // Extract a parameter from an application/x-www-form-urlencoded body.
    Pattern pattern = Pattern.compile("(^|&)" + Pattern.quote(paramName) + "=([^&]+)");
    Matcher matcher = pattern.matcher(body);
    if (matcher.find()) {
      try {
        return URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
      } catch (Exception e) {
        return matcher.group(2);
      }
    }
    return null;
  }

  /**
   * Recalculate the DPoP {@code ath} claim after the access token was manipulated and re-sign the
   * proof JWT.
   */
  private void updateDpopAth(HttpRequest request, String manipulatedAccessToken) {
    if (!Boolean.TRUE.equals(configuration.getUpdateAth())
        || configuration.getDpopLocation() == null
        || configuration.getDpopPrivateKeyPem() == null) {
      return;
    }

    try {
      String[] locationParts = configuration.getDpopLocation().split("\\.");
      if (locationParts.length < 3 || !"$".equals(locationParts[0])) {
        log.warn("Invalid dpopLocation format: {}", configuration.getDpopLocation());
        return;
      }

      String httpPart = locationParts[1];
      String containerName = normalizeLocationSegment(locationParts[2]);

      if (!"header".equals(httpPart)) {
        log.warn("DPoP is expected in header, got: {}", httpPart);
        return;
      }

      String dpopHeaderValue = getHeaderValue(request, containerName);
      if (dpopHeaderValue == null) {
        log.debug("DPoP header {} not found in request", containerName);
        return;
      }

      String prefix = extractJwtHeaderPrefix(dpopHeaderValue);
      String dpopJwt = extractCompactJwt(dpopHeaderValue);

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(manipulatedAccessToken.getBytes(StandardCharsets.US_ASCII));
      String newAth = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

      String[] parts = dpopJwt.split("\\.");
      if (parts.length != 3) {
        log.warn("Invalid DPoP JWT format");
        return;
      }

      String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
      String bodyJson = new String(Base64.getUrlDecoder().decode(parts[1]));
      ObjectNode headerNode = (ObjectNode) mapper.readTree(headerJson);
      ObjectNode bodyNode = (ObjectNode) mapper.readTree(bodyJson);

      bodyNode.put("ath", newAth);
      log.info("Updated DPoP ath to: {}", newAth);

      ECPrivateKey dpopPrivateKey = parseEcPrivateKey(configuration.getDpopPrivateKeyPem());

      String newHeaderB64 =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(mapper.writeValueAsBytes(headerNode));
      String newBodyB64 =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(mapper.writeValueAsBytes(bodyNode));

      String signingInput = newHeaderB64 + "." + newBodyB64;

      byte[] signatureBytes = signEs256(signingInput, dpopPrivateKey);
      String signatureB64 =
          Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

      String newDpopJwt = signingInput + "." + signatureB64;

      updateHeader(request, containerName, prefix + newDpopJwt);
      log.info("DPoP JWT updated with new ath");
    } catch (Exception e) {
      log.error("Failed to update DPoP ath: {}", e.getMessage(), e);
    }
  }

  /**
   * Manipulate a JWT stored in an HTTP header and keep the original authentication prefix such as
   * {@code Bearer } or {@code DPoP }.
   *
   * @return the manipulated JWT without the prefix, or {@code null} when the header is absent
   */
  private String manipulateHeaderJwt(
      HttpRequest request, String headerName, String jwtPart, String fieldName, Integer arrayIndex)
      throws Exception {
    String headerValue = getHeaderValue(request, headerName);
    if (headerValue == null) {
      log.debug("Header {} not found in request", headerName);
      return null;
    }

    String prefix = extractJwtHeaderPrefix(headerValue);
    String jwt = extractCompactJwt(headerValue);
    String manipulatedJwt = manipulateJwt(jwt, jwtPart, fieldName, arrayIndex);
    updateHeader(request, headerName, prefix + manipulatedJwt);
    return manipulatedJwt;
  }

  /** Manipulate a JWT stored in the request body and update the serialized payload in-place. */
  private void manipulateBodyJwt(
      HttpRequest request,
      String containerName,
      String jwtPart,
      String fieldName,
      Integer arrayIndex)
      throws Exception {
    byte[] bodyBytes = request.getBody();
    if (bodyBytes == null || bodyBytes.length == 0) {
      log.debug("Request body is empty");
      return;
    }

    String body = new String(bodyBytes, StandardCharsets.UTF_8);
    String contentType = getHeaderValue(request, "content-type");

    // Token endpoints commonly send JWTs either as JSON fields or as form parameters.
    String newBody;
    if (contentType != null && contentType.contains("application/json")) {
      newBody = manipulateJsonBody(body, containerName, jwtPart, fieldName, arrayIndex);
    } else {
      newBody = manipulateFormBody(body, containerName, jwtPart, fieldName, arrayIndex);
    }

    byte[] newBodyBytes = newBody.getBytes(StandardCharsets.UTF_8);
    request.withBody(newBodyBytes);
    // Keep Content-Length in sync after in-place body manipulation.
    updateHeader(request, "Content-Length", String.valueOf(newBodyBytes.length));
  }

  /** Manipulate a JWT stored in a JSON body field. */
  private String manipulateJsonBody(
      String body, String containerName, String jwtPart, String fieldName, Integer arrayIndex)
      throws Exception {
    JsonNode rootNode = mapper.readTree(body);
    if (!rootNode.has(containerName)) {
      log.debug("JSON field {} not found in body", containerName);
      return body;
    }

    String jwt = rootNode.get(containerName).asText();
    String manipulatedJwt = manipulateJwt(jwt, jwtPart, fieldName, arrayIndex);

    ((ObjectNode) rootNode).put(containerName, manipulatedJwt);
    return mapper.writeValueAsString(rootNode);
  }

  /** Manipulate a JWT stored in a form body parameter. */
  private String manipulateFormBody(
      String body, String containerName, String jwtPart, String fieldName, Integer arrayIndex)
      throws Exception {
    Pattern pattern = Pattern.compile("(^|&)" + Pattern.quote(containerName) + "=([^&]+)");
    Matcher matcher = pattern.matcher(body);

    if (!matcher.find()) {
      log.debug("Form parameter {} not found in body", containerName);
      return body;
    }

    String jwt = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
    String manipulatedJwt = manipulateJwt(jwt, jwtPart, fieldName, arrayIndex);
    String encodedJwt = URLEncoder.encode(manipulatedJwt, StandardCharsets.UTF_8);

    return body.substring(0, matcher.start(2)) + encodedJwt + body.substring(matcher.end(2));
  }

  /** Read one request header value using case-insensitive header-name matching. */
  private static String getHeaderValue(HttpRequest request, String headerName) {
    var headers = request.getHeaderList();
    for (var header : headers) {
      if (header.getName().equalsIgnoreCase(headerName)) {
        return header.getValues().get(0);
      }
    }
    return null;
  }

  /** Replace a request header value while preserving the rest of the request. */
  private static void updateHeader(HttpRequest request, String headerName, String newValue) {
    request.removeHeader(headerName);
    request.withHeader(headerName, newValue);
  }

  /**
   * Return the original authentication prefix from a header JWT value, for example {@code dpop }
   * or {@code Bearer }.
   */
  private static String extractJwtHeaderPrefix(String headerValue) {
    Matcher matcher = JWT_HEADER_PREFIX_PATTERN.matcher(headerValue);
    if (matcher.matches()) {
      return matcher.group(1) + matcher.group(2);
    }
    return "";
  }

  /** Return the compact JWT part from an HTTP header value with an optional auth scheme prefix. */
  private static String extractCompactJwt(String headerValue) {
    Matcher matcher = JWT_HEADER_PREFIX_PATTERN.matcher(headerValue);
    if (matcher.matches()) {
      return matcher.group(3);
    }
    return headerValue;
  }

  /** Modify one JWT field and optionally re-sign the compact JWT afterwards. */
  private String manipulateJwt(String jwt, String jwtPart, String fieldName, Integer arrayIndex)
      throws Exception {
    String[] parts = jwt.split("\\.", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException(
          "Invalid JWT format: expected 3 parts, got " + parts.length);
    }

    if ("variant".equals(jwtPart)) {
      return buildJwtVariant(jwt, parts);
    }

    String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
    String bodyJson = new String(Base64.getUrlDecoder().decode(parts[1]));
    ObjectNode headerNode = (ObjectNode) mapper.readTree(headerJson);
    ObjectNode bodyNode = (ObjectNode) mapper.readTree(bodyJson);

    if ("header".equals(jwtPart)) {
      setJsonValue(headerNode, fieldName, configuration.getReplaceWith(), arrayIndex);
    } else if ("body".equals(jwtPart)) {
      setJsonValue(bodyNode, fieldName, configuration.getReplaceWith(), arrayIndex);
    }

    String newHeaderB64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mapper.writeValueAsBytes(headerNode));
    String newBodyB64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mapper.writeValueAsBytes(bodyNode));

    if (configuration.getPrivateKeyPem() != null && !configuration.getPrivateKeyPem().isBlank()) {
      return signJwt(headerNode, bodyNode);
    }
    return newHeaderB64 + "." + newBodyB64 + "." + parts[2];
  }

  /** Build a malformed compact JWT variant from the live intercepted token. */
  private String buildJwtVariant(String jwt, String[] parts) throws Exception {
    var variant = configuration.getReplaceWith().trim().toLowerCase(Locale.ROOT);
    String headerJson = decodeBase64UrlSegment(parts[0], "header");
    String bodyJson = decodeBase64UrlSegment(parts[1], "body");

    return switch (variant) {
      case "two_segments" -> parts[0] + "." + parts[1];
      case "invalid_header_base64url" -> "###." + parts[1] + "." + parts[2];
      case "invalid_payload_base64url" -> parts[0] + ".###." + parts[2];
      case "invalid_signature_base64url" -> parts[0] + "." + parts[1] + ".###";
      case "invalid_header_json" -> encodeBase64Url("{") + "." + parts[1] + "." + parts[2];
      case "invalid_payload_json" -> parts[0] + "." + encodeBase64Url("{") + "." + parts[2];
      case "jwe_like_five_segments" -> parts[0]
          + ".ZW5jcnlwdGVkS2V5.aXY.Y2lwaGVydGV4dA.dGFn";
      case "unknown_header_parameter" -> signCompactJwt(
          addUnknownHeaderParameter(headerJson), bodyJson);
      case "unsupported_crit" -> signCompactJwt(addUnsupportedCritHeader(headerJson), bodyJson);
      case "unsupported_alg" -> signCompactJwt(
          replaceTopLevelHeaderParameter(headerJson, "alg", "RS999"), bodyJson);
      case "missing_alg" -> signCompactJwt(removeTopLevelHeaderParameter(headerJson, "alg"),
          bodyJson);
      case "duplicate_alg_headers" -> signCompactJwt(duplicateAlgHeader(headerJson), bodyJson);
      case "invalid_signature" -> createInvalidSignatureVariant(headerJson, bodyJson, parts[2]);
      case "nested_cty_jwt_valid_inner" -> signCompactJwt(addNestedJwtContentType(headerJson),
          jwt);
      case "nested_cty_jwt_invalid_inner" -> signCompactJwt(addNestedJwtContentType(headerJson),
          "###.eyJpc3MiOiJ6ZXRhLXRlc3QifQ.signature");
      default -> throw new IllegalArgumentException("Unknown JWT variant: " + variant);
    };
  }

  /** Decode a Base64URL compact JWT segment into UTF-8 text. */
  private static String decodeBase64UrlSegment(String segment, String description) {
    try {
      return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Failed to decode JWT " + description + " segment", e);
    }
  }

  /** Encode UTF-8 text as unpadded Base64URL. */
  private static String encodeBase64Url(String value) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  /** Add an unsupported non-critical JOSE header parameter. */
  private static String addUnknownHeaderParameter(String headerJson) throws Exception {
    var header = parseJsonObject(headerJson, "JOSE header");
    header.put("unknown_guard_parameter", "unsupported");
    return mapper.writeValueAsString(header);
  }

  /** Add an unsupported critical JOSE header parameter. */
  private static String addUnsupportedCritHeader(String headerJson) throws Exception {
    var header = parseJsonObject(headerJson, "JOSE header");
    var crit = header.putArray("crit");
    crit.add("unsupported_guard_parameter");
    header.put("unsupported_guard_parameter", "requested-by-crit");
    return mapper.writeValueAsString(header);
  }

  /** Replace a top-level JOSE header parameter with a string value. */
  private static String replaceTopLevelHeaderParameter(String headerJson, String parameter,
      String value) throws Exception {
    var header = parseJsonObject(headerJson, "JOSE header");
    header.put(parameter, value);
    return mapper.writeValueAsString(header);
  }

  /** Remove a top-level JOSE header parameter. */
  private static String removeTopLevelHeaderParameter(String headerJson, String parameter)
      throws Exception {
    var header = parseJsonObject(headerJson, "JOSE header");
    header.remove(parameter);
    return mapper.writeValueAsString(header);
  }

  /** Duplicate the top-level {@code alg} header member in the raw JOSE header JSON. */
  private static String duplicateAlgHeader(String headerJson) {
    var matcher = Pattern.compile("\"alg\"\\s*:\\s*\"[^\"]+\"").matcher(headerJson);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Original JOSE header must contain an alg parameter");
    }
    return headerJson.substring(0, matcher.start())
        + "\"alg\":\"ES384\","
        + headerJson.substring(matcher.start());
  }

  /** Add {@code cty=JWT} to the JOSE header. */
  private static String addNestedJwtContentType(String headerJson) throws Exception {
    var header = parseJsonObject(headerJson, "JOSE header");
    header.put("cty", "JWT");
    return mapper.writeValueAsString(header);
  }

  /** Create a compact JWS with changed signing input and the unchanged original signature. */
  private static String createInvalidSignatureVariant(String headerJson, String bodyJson,
      String signaturePart) throws Exception {
    var body = parseJsonObject(bodyJson, "JWT body");
    body.put("broken_signature_marker", true);
    return encodeBase64Url(headerJson) + "." + encodeBase64Url(mapper.writeValueAsString(body))
        + "." + signaturePart;
  }

  /** Parse a JSON object or fail with a targeted message. */
  private static ObjectNode parseJsonObject(String json, String description) throws Exception {
    var node = mapper.readTree(json);
    if (!node.isObject()) {
      throw new IllegalArgumentException(description + " must be a JSON object");
    }
    return (ObjectNode) node;
  }

  /** Sign raw JOSE header and payload JSON with the configured EC private key. */
  private String signCompactJwt(String headerJson, String bodyJson) throws Exception {
    if (configuration.getPrivateKeyPem() == null || configuration.getPrivateKeyPem().isBlank()) {
      throw new IllegalArgumentException("A private key is required for signed JWT variants");
    }

    parseJsonObject(headerJson, "JOSE header");
    if (Boolean.TRUE.equals(configuration.getReplaceJwk())) {
      java.security.interfaces.ECPublicKey publicKey = derivePublicKey(
          parseEcPrivateKey(configuration.getPrivateKeyPem()));
      headerJson = replaceJwkHeaderParameter(headerJson, toPublicJwkJson(publicKey));
    }

    var signingInput = encodeBase64Url(headerJson) + "." + encodeBase64Url(bodyJson);
    ECPrivateKey privateKey = parseEcPrivateKey(configuration.getPrivateKeyPem());
    byte[] signatureBytes = signEs256(signingInput, privateKey);
    return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(signatureBytes);
  }

  /** Replace the top-level {@code jwk} header member while preserving raw header edge cases. */
  private static String replaceJwkHeaderParameter(String headerJson, String jwkJson) {
    var matcher = Pattern.compile("\"jwk\"\\s*:\\s*\\{[^{}]*}").matcher(headerJson);
    if (matcher.find()) {
      return headerJson.substring(0, matcher.start())
          + "\"jwk\":"
          + jwkJson
          + headerJson.substring(matcher.end());
    }

    var trimmed = headerJson.trim();
    if (!trimmed.startsWith("{")) {
      throw new IllegalArgumentException("JOSE header must be a JSON object to replace jwk");
    }
    if (trimmed.endsWith("}") && trimmed.substring(1, trimmed.length() - 1).isBlank()) {
      return "{" + "\"jwk\":" + jwkJson + "}";
    }
    return "{" + "\"jwk\":" + jwkJson + "," + trimmed.substring(1);
  }

  /** Apply a value change to a JSON field while preserving the current field shape when possible. */
  private static void setJsonValue(
      ObjectNode node, String key, String value, Integer arrayIndex) {
    // Support dot-notation paths such as client_statement.platform in addition to top-level
    // claims.
    if (key.contains(".")) {
      setNestedJsonValue(node, key, value, arrayIndex);
      return;
    }

    if (node.has(key)) {
      var existingNode = node.get(key);
      if (existingNode.isBoolean()) {
        node.put(key, Boolean.parseBoolean(value));
      } else if (existingNode.isNumber()) {
        node.put(key, Long.parseLong(value));
      } else if (existingNode.isArray()) {
        var arrayNode = (com.fasterxml.jackson.databind.node.ArrayNode) existingNode;
        if (arrayIndex != null) {
          if (arrayIndex >= 0 && arrayIndex < arrayNode.size()) {
            arrayNode.set(arrayIndex, mapper.getNodeFactory().textNode(value));
          } else {
            arrayNode.add(value);
          }
          log.debug("Modified array element at index {}: {}", arrayIndex, value);
        } else if (value.trim().startsWith("[")) {
          try {
            node.set(key, mapper.readTree(value));
          } catch (Exception e) {
            var newArray = mapper.createArrayNode();
            newArray.add(value);
            node.set(key, newArray);
          }
        } else {
          var newArray = mapper.createArrayNode();
          newArray.add(value);
          node.set(key, newArray);
        }
      } else {
        node.put(key, value);
      }
    } else {
      node.put(key, value);
    }
  }

  /**
   * Set a nested JSON value using dot notation such as {@code client_statement.platform}. Missing
   * intermediate objects are created on demand.
   */
  private static void setNestedJsonValue(
      ObjectNode node, String path, String value, Integer arrayIndex) {
    String[] pathParts = path.split("\\.");
    ObjectNode currentNode = node;

    for (int i = 0; i < pathParts.length - 1; i++) {
      String part = pathParts[i];
      if (!currentNode.has(part)) {
        currentNode.set(part, mapper.createObjectNode());
      } else if (!currentNode.get(part).isObject()) {
        log.warn("Replacing non-object value at {} with object to support nested path", part);
        currentNode.set(part, mapper.createObjectNode());
      }
      currentNode = (ObjectNode) currentNode.get(part);
    }

    String finalKey = pathParts[pathParts.length - 1];
    if (currentNode.has(finalKey)) {
      var existingNode = currentNode.get(finalKey);
      if (existingNode.isBoolean()) {
        currentNode.put(finalKey, Boolean.parseBoolean(value));
      } else if (existingNode.isNumber()) {
        try {
          currentNode.put(finalKey, Long.parseLong(value));
        } catch (NumberFormatException e) {
          log.warn(
              "Invalid numeric value '{}' for field '{}', treating as string", value, finalKey);
          currentNode.put(finalKey, value);
        }
      } else if (existingNode.isArray() && arrayIndex != null) {
        var arrayNode = (com.fasterxml.jackson.databind.node.ArrayNode) existingNode;
        if (arrayIndex >= 0 && arrayIndex < arrayNode.size()) {
          arrayNode.set(arrayIndex, mapper.getNodeFactory().textNode(value));
        } else {
          arrayNode.add(value);
        }
      } else {
        currentNode.put(finalKey, value);
      }
    } else {
      currentNode.put(finalKey, value);
    }
    log.info("Set nested value: {} = {}", path, value);
  }

  /** Re-sign a manipulated JWT with the configured EC private key. */
  private String signJwt(ObjectNode headerNode, ObjectNode bodyNode) throws Exception {
    ECPrivateKey privateKey = parseEcPrivateKey(configuration.getPrivateKeyPem());

    // Replace the embedded JWK only when explicitly requested for negative-test scenarios.
    if (Boolean.TRUE.equals(configuration.getReplaceJwk())) {
      java.security.interfaces.ECPublicKey publicKey = derivePublicKey(privateKey);
      headerNode.set("jwk", mapper.readTree(toPublicJwkJson(publicKey)));
      log.info("JWK replaced with public key derived from provided private key");
    }

    // Build the compact JWT manually so tests can keep a manipulated header.alg value while we
    // still sign with ES256.
    String headerJson = mapper.writeValueAsString(headerNode);
    String bodyJson = mapper.writeValueAsString(bodyNode);

    String headerB64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
    String bodyB64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));

    String signingInput = headerB64 + "." + bodyB64;

    byte[] signatureBytes = signEs256(signingInput, privateKey);

    String signatureB64 =
        Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

    return signingInput + "." + signatureB64;
  }

  /** Serialize an EC public key as a public JWK using the jose4j stack already used by RBEL. */
  private static String toPublicJwkJson(java.security.interfaces.ECPublicKey publicKey) {
    return new EllipticCurveJsonWebKey(publicKey).toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
  }

  /** Sign a pre-built compact-JWS signing input with ES256 and return JOSE raw R/S bytes. */
  private static byte[] signEs256(String signingInput, ECPrivateKey privateKey) throws Exception {
    Signature signature = Signature.getInstance("SHA256withECDSA");
    signature.initSign(privateKey);
    signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
    return EcdsaUsingShaAlgorithm.convertDerToConcatenated(signature.sign(), 64);
  }

  /** Derive the corresponding P-256 public key from the configured EC private key. */
  private static java.security.interfaces.ECPublicKey derivePublicKey(ECPrivateKey privateKey)
      throws Exception {
    org.bouncycastle.jce.spec.ECParameterSpec bcSpec =
        org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("P-256");
    org.bouncycastle.math.ec.ECPoint q = bcSpec.getG().multiply(privateKey.getS()).normalize();

    java.security.spec.ECPoint pubPoint =
        new java.security.spec.ECPoint(
            q.getAffineXCoord().toBigInteger(), q.getAffineYCoord().toBigInteger());

    java.security.spec.ECPublicKeySpec pubSpec =
        new java.security.spec.ECPublicKeySpec(pubPoint, privateKey.getParams());
    KeyFactory kf = KeyFactory.getInstance("EC");
    return (java.security.interfaces.ECPublicKey) kf.generatePublic(pubSpec);
  }

  /** Parse an EC private key from PEM or raw PKCS#8 base64 input. */
  private static ECPrivateKey parseEcPrivateKey(String pemValue) throws Exception {
    if (pemValue == null || pemValue.isBlank()) {
      throw new IllegalArgumentException("EC private key must not be empty");
    }

    // Accept PEM-encoded keys as well as raw base64 PKCS#8 to preserve compatibility with the
    // existing test inputs and controller payloads.
    String trimmedValue = pemValue.trim();

    if (trimmedValue.startsWith("-----BEGIN")) {
      try (PEMParser pemParser = new PEMParser(new StringReader(trimmedValue))) {
        Object parsedObject = pemParser.readObject();
        if (parsedObject == null) {
          throw new IllegalArgumentException("EC private key PEM could not be parsed");
        }

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("SunEC");
        PrivateKey privateKey;
        log.info("PEM parsed object type: {}", parsedObject.getClass().getSimpleName());
        if (parsedObject instanceof PEMKeyPair pemKeyPair) {
          privateKey = converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());
        } else if (parsedObject instanceof PrivateKeyInfo privateKeyInfo) {
          privateKey = converter.getPrivateKey(privateKeyInfo);
        } else {
          throw new IllegalArgumentException(
              "Unsupported EC private key format: "
                  + parsedObject.getClass().getSimpleName());
        }

        log.info(
            "Converted key type: {}, algorithm: {}",
            privateKey.getClass().getSimpleName(),
            privateKey.getAlgorithm());
        if (!(privateKey instanceof ECPrivateKey ecPrivateKey)) {
          throw new IllegalArgumentException(
              "Provided key is not an EC private key, got: "
                  + privateKey.getClass().getSimpleName()
                  + " with algorithm: "
                  + privateKey.getAlgorithm());
        }
        return ecPrivateKey;
      } catch (IllegalArgumentException e) {
        throw e;
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to parse EC private key: " + e.getMessage(), e);
      }
    }

    return decodePkcs8Base64(trimmedValue);
  }

  /** Decode a raw PKCS#8 base64 EC private key. */
  private static ECPrivateKey decodePkcs8Base64(String base64Key) throws Exception {
    byte[] decoded = Base64.getDecoder().decode(base64Key.replaceAll("\\s", ""));
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
    KeyFactory kf = KeyFactory.getInstance("EC");
    return (ECPrivateKey) kf.generatePrivate(spec);
  }

  /**
   * Split a condition string by an operator while ignoring operator tokens that appear inside
   * quoted string literals.
   */
  private static String[] splitConditionOperator(String conditionStr, String operator) {
    java.util.List<String> parts = new java.util.ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    char quoteChar = 0;

    for (int i = 0; i < conditionStr.length(); i++) {
      char c = conditionStr.charAt(i);

      if ((c == '"' || c == '\'') && (i == 0 || conditionStr.charAt(i - 1) != '\\')) {
        if (!inQuotes) {
          inQuotes = true;
          quoteChar = c;
        } else if (c == quoteChar) {
          inQuotes = false;
        }
        current.append(c);
      } else if (!inQuotes
          && i + operator.length() <= conditionStr.length()
          && conditionStr.startsWith(operator, i)) {
        parts.add(current.toString());
        current = new StringBuilder();
        i += operator.length() - 1;
      } else {
        current.append(c);
      }
    }

    if (!current.isEmpty()) {
      parts.add(current.toString());
    }

    return parts.toArray(new String[0]);
  }
}
