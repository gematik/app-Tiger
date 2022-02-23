package de.gematik.test.tiger.testenvmgr.servers;

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.testenvmgr.InsecureTrustAllManager;
import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.net.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.awaitility.core.ConditionTimeoutException;

@Slf4j
public abstract class AbstractExternalTigerServer extends TigerServer {

    AbstractExternalTigerServer(String hostname, String serverId, CfgServer configuration,
        TigerTestEnvMgr tigerTestEnvMgr) {
        super(hostname, serverId, tigerTestEnvMgr, configuration);
    }

    void waitForService(boolean quiet) {
        final long timeOutInMs = getStartupTimeoutSec().orElse(DEFAULT_STARTUP_TIMEOUT_IN_SECONDS) * 1000L;
        if (isHealthCheckNone()) {
            waitForConfiguredTimeAndSetRunning(timeOutInMs);
        } else {
            if (!quiet) {
                log.info("  Checking {} instance '{}' is available ...", getClass().getSimpleName(), getHostname());
            }
            try {
                await().atMost(Math.max(timeOutInMs, 1000), TimeUnit.MILLISECONDS)
                    .pollInterval(750, TimeUnit.MILLISECONDS)
                    .until(() -> updateStatus(quiet) != TigerServerStatus.STARTING);
            } catch (ConditionTimeoutException cte) {
                if (!quiet) {
                    throw new TigerTestEnvException("Timeout waiting for external server to respond at '"
                        + getConfiguration().getExternalJarOptions().getHealthcheck() + "'!");
                }
            }
        }
    }

    public TigerServerStatus updateStatus(boolean quiet) {
        var url = buildHealthcheckUrl();
        try {
            URLConnection con = url.openConnection();
            InsecureTrustAllManager.allowAllSsl(con);
            con.setConnectTimeout(1000);
            con.connect();
            printServerUpMessage();
            setStatus(TigerServerStatus.RUNNING);
        } catch (ConnectException | SocketTimeoutException cex) {
            if (!quiet) {
                log.info("No connection to " + url + " of " + getHostname() + "...");
            }
        } catch (SSLHandshakeException sslhe) {
            log.warn(Ansi.colorize("SSL handshake but server at least seems to be up!" + sslhe.getMessage(),
                RbelAnsiColors.YELLOW_BOLD));
            setStatus(TigerServerStatus.RUNNING);
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

    void printServerUpMessage() {
        String message = "External server Startup OK for '" + getHostname();
        if (getConfiguration().getSource() != null
            && !getConfiguration().getSource().isEmpty()) {
             message += "downloaded from" + getConfiguration().getSource().get(0);
        }
        log.info(Ansi.colorize(message, RbelAnsiColors.GREEN_BOLD));
    }

    private void waitForConfiguredTimeAndSetRunning(long timeOutInMs) {
        log.warn("No health check URL configured! Resorting to simple wait with timeout {}s", (timeOutInMs / 1000L));
        log.info("Waiting {}s for external server {}...", (timeOutInMs / 1000L), getHostname());
        try {
            Thread.sleep(timeOutInMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        setStatus(TigerServerStatus.RUNNING);
    }

    URL buildHealthcheckUrl() {
        try {
            return new URL(getHealthcheckUrl());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                "Could not build healthcheck URL from '" + getConfiguration().getExternalJarOptions().getHealthcheck()
                    + "'!", e);
        }
    }

    String getHealthcheckUrl() {
        return getConfiguration().getExternalJarOptions().getHealthcheck();
    }

    @Override
    public String getDestinationUrl(String fallbackProtocol) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(getHealthcheckUrl()).setPath("");
            if (StringUtils.isNotEmpty(fallbackProtocol)) {
                uriBuilder.setScheme(fallbackProtocol);
            }
            return uriBuilder.build().toURL().toString();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new TigerEnvironmentStartupException("Unable to build destination URL", e);
        }
    }

    boolean isHealthCheckNone() {
        return getConfiguration().getExternalJarOptions() == null ||
            getConfiguration().getExternalJarOptions().getHealthcheck() == null ||
            getConfiguration().getExternalJarOptions().getHealthcheck().isEmpty() ||
            getConfiguration().getExternalJarOptions().getHealthcheck().equals("NONE");
    }
}
