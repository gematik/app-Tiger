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
package de.gematik.test.tiger.testenvmgr.servers;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.EXTERNAL_SERVER_CONNECTION_TIMEOUT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.EXTERNAL_SERVER_STARTUP_POLL_INTERVAL_IN_MS;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.web.InsecureTrustAllManager;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.ProxyConfigurationConverter;
import de.gematik.test.tiger.proxy.handler.TigerExceptionUtils;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.IOException;
import java.net.*;
import java.net.Proxy.Type;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.awaitility.core.ConditionTimeoutException;
import org.bouncycastle.tls.TlsException;

public abstract class AbstractExternalTigerServer extends AbstractTigerServer {

  public static final String SERVER = "Server ";

  /**
   * Container to store exceptions while performing startup of server, useful if you start external
   * processes and want to monitor them in a separate thread...
   *
   * @see de.gematik.test.tiger.testenvmgr.servers.ExternalJarServer
   */
  protected final AtomicReference<Throwable> startupException = new AtomicReference<>();

  protected AbstractExternalTigerServer(
      String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  protected void waitForServerUp() {
    if (getStatus() == TigerServerStatus.NEW) {
      setStatus(TigerServerStatus.STARTING);
    }
    waitForServiceToBeUpForHalfOfTheConnectionTimeout(true);
    TigerTestEnvException exceptionAtStartup =
        new TigerTestEnvException(
            startupException.get(),
            "Unable to start %s '%s' (Status %s)!",
            getConfiguration().getType(),
            getServerId(),
            getStatus().toString());
    if (startupException.get() != null) {
      throw exceptionAtStartup;
    } else if (getStatus() == TigerServerStatus.STOPPED) {
      throw new TigerTestEnvException(
          "%s Server %s stopped unexpectedly!", getConfiguration().getType(), getServerId());
    } else if (getStatus() == TigerServerStatus.STARTING) {
      waitForServiceToBeUpForHalfOfTheConnectionTimeout(false);
      if (startupException.get() != null) {
        throw exceptionAtStartup;
      } else {
        if (getStatus() != TigerServerStatus.RUNNING) {
          throw new TigerTestEnvException(
              "%s Server %s still not running (Status %s)!",
              getConfiguration().getType(), getServerId(), getStatus().toString());
        }
      }
    }
    statusMessage(getConfiguration().getType() + " " + getServerId() + " started");
  }

  protected void waitForServiceToBeUpForHalfOfTheConnectionTimeout(boolean quiet) {
    final long timeOutInMs =
        getStartupTimeoutSec().orElse(DEFAULT_STARTUP_TIMEOUT_IN_SECONDS) * 1000L / 2;
    final int pollIntervalMs =
        Optional.ofNullable(getConfiguration())
            .map(CfgServer::getStartupPollIntervalMs)
            .orElseGet(EXTERNAL_SERVER_STARTUP_POLL_INTERVAL_IN_MS::getValueOrDefault);

    if (isHealthCheckNone()) {
      waitForConfiguredTimeAndSetRunning(timeOutInMs);
    } else {
      if (!quiet) {
        log.info(
            "  Checking {} instance '{}' is available ...",
            getClass().getSimpleName(),
            getServerId());
      }
      try {
        long maxTimeOutMs = Math.max(timeOutInMs, 1000);
        long pollInterval = Math.min(pollIntervalMs, maxTimeOutMs - 1); // cap at maxTimeOut
        await()
            .atMost(maxTimeOutMs, TimeUnit.MILLISECONDS)
            .pollInterval(pollInterval, TimeUnit.MILLISECONDS)
            .until(
                () ->
                    updateStatus(quiet) != TigerServerStatus.STARTING
                        && getStatus() != TigerServerStatus.NEW);
      } catch (ConditionTimeoutException cte) {
        if (!quiet) {
          throw new TigerTestEnvException(
              "Timeout waiting for external server '"
                  + getServerId()
                  + "' to respond at '"
                  + getHealthcheckUrl().orElse("<null>")
                  + "'!");
        }
      }
    }
  }

  public TigerServerStatus updateStatus(boolean noErrorLogging) {
    var url = buildHealthcheckUrl();
    if (!noErrorLogging) {
      statusMessage("Waiting for URL '" + url + "' to be healthy...");
    }
    try {
      checkUrlOrThrowException(url);
      printServerUpMessage();
      setStatus(TigerServerStatus.RUNNING, SERVER + getServerId() + " up & healthy");
    } catch (ConnectException | SocketTimeoutException cex) {
      if (!noErrorLogging) {
        handleNoTcpConnectionException(url);
      }
    } catch (SSLHandshakeException | TlsException sslhe) {
      handleSslHandshakeErrorAndSetServerRunning(sslhe);
    } catch (SSLException sslex) {
      handleOtherSslError(noErrorLogging, sslex);
    } catch (Exception e) {
      if (!noErrorLogging) {
        handleOtherException(e);
      }
    }
    return getStatus();
  }

  private void handleNoTcpConnectionException(URL url) {
    log.info("No connection to {} of {}...", url, getServerId());
  }

  private void handleOtherException(Exception e) {
    log.error("Failed to connect - " + e.getMessage(), e);
  }

  private void handleOtherSslError(boolean noErrorLogging, SSLException sslex) {
    if (sslex.getMessage().equals("Unsupported or unrecognized SSL message")) {
      if (!noErrorLogging) {
        log.error("Unsupported or unrecognized SSL message - MAYBE you mismatched http/httpS?");
      }
    } else {
      if (!noErrorLogging) {
        log.error("SSL Error - " + sslex.getMessage(), sslex);
      }
    }
  }

  private void handleSslHandshakeErrorAndSetServerRunning(IOException sslhe) {
    if (log.isWarnEnabled()) {
      log.warn(
          Ansi.colorize(
              "SSL handshake but server at least seems to be up! {}", RbelAnsiColors.YELLOW_BOLD),
          sslhe.getMessage());
    }
    setStatus(TigerServerStatus.RUNNING, SERVER + getServerId() + " up & healthy");
  }

  private void checkUrlOrThrowException(URL url) throws IOException {
    final URLConnection con = getUrlConnectionToServer(url);
    if (con instanceof HttpURLConnection httpConnection) {
      InsecureTrustAllManager.allowAllSsl(con);
      con.setConnectTimeout(EXTERNAL_SERVER_CONNECTION_TIMEOUT.getValueOrDefault());
      con.connect();
      try {
        final int responseCode = httpConnection.getResponseCode();
        if (getConfiguration().getHealthcheckReturnCode() != null
            && !getConfiguration().getHealthcheckReturnCode().equals(responseCode)) {
          throw new TigerEnvironmentStartupException(
              "Return code for server '%s' does not match: %nExpected %d but got %d",
              getServerId(), getConfiguration().getHealthcheckReturnCode(), responseCode);
        }
      } catch (SocketException e) {
        if (e.getClass() != SocketException.class
            || getConfiguration().getHealthcheckReturnCode() != null
            || TigerExceptionUtils.getCauseWithMessageMatching(
                    e,
                    message ->
                        "Connection reset".equals(message)
                            || "Unexpected end of file from server".equals(message))
                .isEmpty()) {
          throw e;
        }
        // ignore
      }
    }
  }

  private URLConnection getUrlConnectionToServer(URL url) throws IOException {
    final Optional<ProxyConfiguration> forwardProxyInfo = createProxyConfiguration();

    if (forwardProxyInfo.isPresent() && !url.getHost().matches("((127\\.0\\.0\\.1)|(localhost))")) {
      Proxy proxy =
          new Proxy(
              Type.HTTP,
              new InetSocketAddress(
                  forwardProxyInfo.get().getProxyAddress().getHostName(),
                  forwardProxyInfo.get().getProxyAddress().getPort()));
      return url.openConnection(proxy);
    } else {
      return url.openConnection();
    }
  }

  private Optional<ProxyConfiguration> createProxyConfiguration() {
    return this.getTigerTestEnvMgr()
        .getLocalTigerProxyOptional()
        .map(TigerProxy::getTigerProxyConfiguration)
        .flatMap(
            ProxyConfigurationConverter::convertForwardProxyConfigurationToMockServerConfiguration)
        .filter(pc -> pc.getProxyAddress() != null)
        .filter(pc -> StringUtils.isNotEmpty(pc.getProxyAddress().getHostName()));
  }

  void printServerUpMessage() {
    String message = "External server Startup OK for '" + getServerId() + "'";
    if (getConfiguration().getSource() != null && !getConfiguration().getSource().isEmpty()) {
      message += " downloaded from '" + getConfiguration().getSource().get(0) + "'";
    }
    if (log.isInfoEnabled()) {
      log.info(Ansi.colorize(message, RbelAnsiColors.GREEN_BOLD));
    }
  }

  private void waitForConfiguredTimeAndSetRunning(long timeOutInMs) {
    log.warn(
        "No health check URL configured! Resorting to simple wait with timeout {}s",
        (timeOutInMs / 1000L));
    log.info("Waiting {}s for external server {}...", (timeOutInMs / 1000L), getServerId());
    try {
      Thread.sleep(timeOutInMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    setStatus(
        TigerServerStatus.RUNNING, SERVER + getServerId() + " up & healthy (default timeout)");
  }

  URL buildHealthcheckUrl() {
    try {
      return new URL(
          getHealthcheckUrl()
              .orElseThrow(
                  () ->
                      new TigerTestEnvException(
                          "No Healthcheck Url is set for server " + getServerId())));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(
          "Could not build healthcheck URL from '" + getHealthcheckUrl() + "'!", e);
    }
  }

  public Optional<String> getHealthcheckUrl() {
    return Optional.ofNullable(getConfiguration().getHealthcheckUrl());
  }

  @Override
  public String getDestinationUrl(String fallbackProtocol) {
    try {
      final URIBuilder uriBuilder =
          new URIBuilder(
                  getHealthcheckUrl()
                      .orElseThrow(
                          () ->
                              new TigerTestEnvException(
                                  "No Healthcheck Url is set for server " + getServerId())))
              .setPath("");
      if (StringUtils.isNotEmpty(fallbackProtocol)) {
        uriBuilder.setScheme(fallbackProtocol);
      }
      return uriBuilder.build().toURL().toString();
    } catch (MalformedURLException | URISyntaxException e) {
      throw new TigerEnvironmentStartupException("Unable to build destination URL", e);
    }
  }

  boolean isHealthCheckNone() {
    return getHealthcheckUrl()
        .filter(StringUtils::isNotEmpty)
        .filter(s -> !s.equals("NONE"))
        .isEmpty();
  }

  protected void applyEnvPropertiesToProcess(ProcessBuilder processBuilder) {
    processBuilder
        .environment()
        .putAll(
            getEnvironmentProperties().stream()
                .map(str -> str.split("=", 2))
                .filter(ar -> ar.length == 2)
                .collect(Collectors.toMap(ar -> ar[0].trim(), ar -> ar[1].trim())));
  }
}
