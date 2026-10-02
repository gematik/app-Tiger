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
package de.gematik.test.tiger.mockserver.socket.tls;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import org.bouncycastle.tls.CipherSuite;

public final class TlsHandshakeDiagnostics {

  private static final Map<Integer, String> CIPHER_SUITE_NAMES = loadCipherSuiteNames();

  private TlsHandshakeDiagnostics() {}

  public static List<String> cipherSuiteNames(int[] cipherSuites) {
    return Arrays.stream(cipherSuites)
        .mapToObj(
            cipherSuite ->
                CIPHER_SUITE_NAMES.getOrDefault(
                    cipherSuite, "UNKNOWN_CIPHER_SUITE_0x%04X".formatted(cipherSuite)))
        .distinct()
        .toList();
  }

  public static Optional<String> cipherSuiteMismatch(
      int[] clientCipherSuites, List<String> serverCipherSuites, TigerPkiIdentity serverIdentity) {
    return cipherSuiteMismatch(
        clientCipherSuites == null ? null : cipherSuiteNames(clientCipherSuites),
        serverCipherSuites,
        serverIdentity);
  }

  static Optional<String> cipherSuiteMismatch(
      List<String> clientCipherSuites,
      List<String> serverCipherSuites,
      TigerPkiIdentity serverIdentity) {
    if (clientCipherSuites == null || serverCipherSuites == null) {
      return Optional.empty();
    }

    Set<String> clientSuites = new LinkedHashSet<>(clientCipherSuites);
    Set<String> serverSuites = new LinkedHashSet<>(serverCipherSuites);
    boolean compatibleSuiteExists =
        clientSuites.stream()
            .anyMatch(
                suite ->
                    serverSuites.contains(suite)
                        && isCompatibleWithServerCertificate(suite, serverIdentity));
    if (compatibleSuiteExists) {
      return Optional.empty();
    }

    List<String> allSuites = new ArrayList<>(clientSuites);
    serverSuites.stream().filter(suite -> !clientSuites.contains(suite)).forEach(allSuites::add);
    int nameWidth =
        Math.max("Name".length(), allSuites.stream().mapToInt(String::length).max().orElse(0));
    String rowFormat = "| %-" + nameWidth + "s | %-7s | %-7s | %s%n";
    StringBuilder table =
        new StringBuilder(
            "TLS handshake failed because client and server have no mutually usable cipher suite.%n"
                .formatted());
    table.append(rowFormat.formatted("Name", "client?", "server?", "note"));
    table.append("-".repeat(nameWidth + 39)).append(System.lineSeparator());
    for (String suite : allSuites) {
      boolean offeredByClient = clientSuites.contains(suite);
      boolean allowedByServer = serverSuites.contains(suite);
      String note = determineNote(suite, offeredByClient, allowedByServer, serverIdentity);
      table.append(
          rowFormat.formatted(
              suite, offeredByClient ? "yes" : "no", allowedByServer ? "yes" : "no", note));
    }
    return Optional.of(table.toString().stripTrailing());
  }

  public static String certificateUnknown(TigerPkiIdentity serverIdentity) {
    String certificateDescription =
        Optional.ofNullable(serverIdentity)
            .map(TigerPkiIdentity::getCertificate)
            .map(TlsHandshakeDiagnostics::describeCertificate)
            .orElse("<certificate details unavailable>");
    return String.join(
        System.lineSeparator(),
        "TLS handshake failed because the client rejected the server certificate as unknown.",
        "Presented server certificate:",
        certificateDescription,
        "Suggested actions:",
        "  - Add the issuing CA or certificate to the client's truststore.",
        "    TigerProxy default certificates:",
        "    https://github.com/gematik/app-Tiger/tree/master/tiger-proxy/src/main/resources",
        "  - Configure the server to present a certificate trusted by the client.");
  }

  public static boolean causeMessageContains(Throwable throwable, String text) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (String.valueOf(current.getMessage()).contains(text)) {
        return true;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return false;
  }

  private static String determineNote(
      String suite,
      boolean offeredByClient,
      boolean allowedByServer,
      TigerPkiIdentity serverIdentity) {
    if (!offeredByClient) {
      return "disallowed by client-hello";
    }
    if (!allowedByServer) {
      return "disallowed by server-suites";
    }
    if (!isCompatibleWithServerCertificate(suite, serverIdentity)) {
      return "incompatible with server-certificate";
    }
    return "not usable by TLS provider";
  }

  private static boolean isCompatibleWithServerCertificate(
      String cipherSuite, TigerPkiIdentity serverIdentity) {
    if (serverIdentity == null || serverIdentity.getCertificate() == null) {
      return true;
    }
    String keyAlgorithm = serverIdentity.getCertificate().getPublicKey().getAlgorithm();
    if (cipherSuite.contains("_ECDSA_")) {
      return "EC".equalsIgnoreCase(keyAlgorithm) || "ECDSA".equalsIgnoreCase(keyAlgorithm);
    }
    if (cipherSuite.contains("_RSA_")) {
      return "RSA".equalsIgnoreCase(keyAlgorithm);
    }
    return true;
  }

  private static String describeCertificate(X509Certificate certificate) {
    return String.join(
        System.lineSeparator(),
        "  Subject: " + certificate.getSubjectX500Principal(),
        "  Issuer: " + certificate.getIssuerX500Principal(),
        "  Serial: " + certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
        "  SHA-256 fingerprint: " + sha256Fingerprint(certificate));
  }

  private static String sha256Fingerprint(X509Certificate certificate) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
      return java.util.HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
    } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
      return "<unavailable: " + e.getMessage() + ">";
    }
  }

  private static Map<Integer, String> loadCipherSuiteNames() {
    return Arrays.stream(CipherSuite.class.getFields())
        .filter(
            field ->
                field.getType() == int.class
                    && Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers()))
        .collect(
            Collectors.toUnmodifiableMap(
                TlsHandshakeDiagnostics::readCipherSuiteValue,
                Field::getName,
                (first, ignored) -> first));
  }

  private static int readCipherSuiteValue(Field field) {
    try {
      return field.getInt(null);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Unable to read TLS cipher suite " + field.getName(), e);
    }
  }
}
