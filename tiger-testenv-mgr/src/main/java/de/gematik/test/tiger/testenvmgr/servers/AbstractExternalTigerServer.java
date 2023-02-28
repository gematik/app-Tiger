/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.util.InsecureTrustAllManager;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.IOException;
import java.net.*;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.awaitility.core.ConditionTimeoutException;

public abstract class AbstractExternalTigerServer extends AbstractTigerServer {

    /** Container to store exceptions while performing startup of server, useful if you start external processes
     * and want to monitor them in a separate thread...
     * @see de.gematik.test.tiger.testenvmgr.servers.ExternalJarServer
     */
    protected final AtomicReference<Throwable> startupException = new AtomicReference<>();

    AbstractExternalTigerServer(String hostname, String serverId, CfgServer configuration,
        TigerTestEnvMgr tigerTestEnvMgr) {
        super(hostname, serverId, tigerTestEnvMgr, configuration);
    }

    protected void waitForServerUp() {
        if (getStatus() == TigerServerStatus.NEW) {
            setStatus(TigerServerStatus.STARTING);
        }
        waitForServiceHalfTime(true);
        TigerTestEnvException exceptionAtStartup = new TigerTestEnvException(
            startupException.get(), "Unable to start %s '%s' (Status %s)!",
            getConfiguration().getType().value(), getServerId(), getStatus().toString());
        if (startupException.get() != null) {
            throw exceptionAtStartup;
        } else if (getStatus() == TigerServerStatus.STOPPED) {
            throw new TigerTestEnvException("%s Server %s stopped unexpectedly!",
                getConfiguration().getType().value(), getServerId());
        } else if (getStatus() == TigerServerStatus.STARTING) {
            waitForServiceHalfTime(false);
            if (startupException.get() != null) {
                throw exceptionAtStartup;
            } else {
                if (getStatus() != TigerServerStatus.RUNNING) {
                    throw new TigerTestEnvException("%s Server %s still not running (Status %s)!",
                        getConfiguration().getType().value(), getServerId(), getStatus().toString());
                }
            }
        }
        statusMessage(getConfiguration().getType().value() + " " + getServerId() + " started");
    }

    protected void waitForServiceHalfTime(boolean quiet) {
        final long timeOutInMs = getStartupTimeoutSec().orElse(DEFAULT_STARTUP_TIMEOUT_IN_SECONDS) * 1000L / 2;
        if (isHealthCheckNone()) {
            waitForConfiguredTimeAndSetRunning(timeOutInMs);
        } else {
            if (!quiet) {
                log.info("  Checking {} instance '{}' is available ...", getClass().getSimpleName(), getServerId());
            }
            try {
                await().atMost(Math.max(timeOutInMs, 1000), TimeUnit.MILLISECONDS)
                    .pollInterval(200, TimeUnit.MILLISECONDS)
                    .until(() -> updateStatus(quiet) != TigerServerStatus.STARTING
                        && getStatus() != TigerServerStatus.NEW);
            } catch (ConditionTimeoutException cte) {
                if (!quiet) {
                    throw new TigerTestEnvException("Timeout waiting for external server '"
                        + getServerId() + "' to respond at '" + getHealthcheckUrl().orElse("<null>") + "'!");
                }
            }
        }
    }

    public TigerServerStatus updateStatus(boolean quiet) {
        var url = buildHealthcheckUrl();
        if (!quiet) {
            statusMessage("Waiting for URL '" + url + "' to be healthy...");
        }
        try {
            checkUrlOrThrowException(url);
            printServerUpMessage();
            setStatus(TigerServerStatus.RUNNING, "Server " + getServerId() +" up & healthy");
        } catch (ConnectException | SocketTimeoutException cex) {
            if (!quiet) {
                log.info("No connection to {} of {}...", url, getServerId());
            }
        } catch (SSLHandshakeException sslhe) {
            log.warn(Ansi.colorize("SSL handshake but server at least seems to be up!" + sslhe.getMessage(),
                RbelAnsiColors.YELLOW_BOLD));
            setStatus(TigerServerStatus.RUNNING, "Server " + getServerId() + " up & healthy");
        } catch (SSLException sslex) {
            if (sslex.getMessage().equals("Unsupported or unrecognized SSL message")) {
                if (!quiet) {
                    log.error("Unsupported or unrecognized SSL message - MAYBE you mismatched http/httpS?");
                }
            } else {
                if (!quiet) {
                    log.error("SSL Error - " + sslex.getMessage(), sslex);
                }
            }
        } catch (Exception e) {
            if (!quiet) {
                log.error("Failed to connect - " + e.getMessage(), e);
            }
        }
        return getStatus();
    }

    private void checkUrlOrThrowException(URL url) throws IOException {
        URLConnection con = url.openConnection();
        InsecureTrustAllManager.allowAllSsl(con);
        con.setConnectTimeout(1000);
        con.connect();
        if (getConfiguration().getHealthcheckReturnCode() != null
            && con instanceof HttpURLConnection) {
            final HttpURLConnection httpConnection = (HttpURLConnection) con;
            if (!getConfiguration().getHealthcheckReturnCode()
                .equals(httpConnection.getResponseCode())) {
                throw new TigerEnvironmentStartupException(
                    "Return code for server '%s' does not match: %nExpected %d but got %d",
                    getServerId(),  getConfiguration().getHealthcheckReturnCode(), httpConnection.getResponseCode());
            }
        }
    }

    void printServerUpMessage() {
        String message = "External server Startup OK for '" + getServerId() + "'";
        if (getConfiguration().getSource() != null
            && !getConfiguration().getSource().isEmpty()) {
            message += " downloaded from '" + getConfiguration().getSource().get(0) + "'";
        }
        log.info(Ansi.colorize(message, RbelAnsiColors.GREEN_BOLD));
    }

    private void waitForConfiguredTimeAndSetRunning(long timeOutInMs) {
        log.warn("No health check URL configured! Resorting to simple wait with timeout {}s", (timeOutInMs / 1000L));
        log.info("Waiting {}s for external server {}...", (timeOutInMs / 1000L), getServerId());
        try {
            Thread.sleep(timeOutInMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        setStatus(TigerServerStatus.RUNNING, "Server " + getServerId() +" up & healthy (default timeout)");
    }

    URL buildHealthcheckUrl() {
        try {
            return new URL(getHealthcheckUrl().orElseThrow(
                () -> new TigerTestEnvException("No Healthcheck Url is set for server " + getServerId()))
            );
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                "Could not build healthcheck URL from '" + getConfiguration().getHealthcheckUrl()
                    + "'!", e);
        }
    }

    Optional<String> getHealthcheckUrl() {
        return Optional.ofNullable(getConfiguration().getHealthcheckUrl());
    }

    @Override
    public String getDestinationUrl(String fallbackProtocol) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(getHealthcheckUrl().orElseThrow(
                    () -> new TigerTestEnvException("No Healthcheck Url is set for server " + getServerId()))
            ).setPath("");
            if (StringUtils.isNotEmpty(fallbackProtocol)) {
                uriBuilder.setScheme(fallbackProtocol);
            }
            return uriBuilder.build().toURL().toString();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new TigerEnvironmentStartupException("Unable to build destination URL", e);
        }
    }

    boolean isHealthCheckNone() {
        return getConfiguration().getHealthcheckUrl() == null ||
            getConfiguration().getHealthcheckUrl().isEmpty() ||
            getConfiguration().getHealthcheckUrl().equals("NONE");
    }

    protected void applyEnvPropertiesToProcess(ProcessBuilder processBuilder) {
        processBuilder.environment().putAll(getEnvironmentProperties().stream()
            .map(str -> str.split("=", 2))
            .filter(ar -> ar.length == 2)
            .collect(Collectors.toMap(
                ar -> ar[0].trim(),
                ar -> ar[1].trim()
            )));
    }

}
