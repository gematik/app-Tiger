package de.gematik.test.tiger.testenvmgr.servers;

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.testenvmgr.InsecureTrustAllManager;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.net.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;

@Slf4j
public abstract class AbstractExternalTigerServer extends TigerServer {

    AbstractExternalTigerServer(String hostname, String serverId, CfgServer configuration,
        TigerTestEnvMgr tigerTestEnvMgr) {
        super(hostname, serverId, configuration, tigerTestEnvMgr);
    }

    boolean waitForService(boolean quiet) {
        final long timeOutInMs = getStartupTimeoutSec().orElse(DEFAULT_STARTUP_TIMEOUT_IN_SECONDS) * 1000L;
        if (isHealthCheckNone()) {
            log.info("Waiting {}s to get external server {} online...", (timeOutInMs / 1000L), getHostname());
            try {
                Thread.sleep(timeOutInMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        if (!quiet) {
            log.info("  Checking {} instance '{}' is available ...", getClass().getSimpleName(), getHostname());
        }
        try {
            var url = getHealthcheckUrl();
            await().atMost(timeOutInMs, TimeUnit.MILLISECONDS)
                .pollDelay(1, TimeUnit.SECONDS)
                .until(() -> {
                    URLConnection con = url.openConnection();
                    InsecureTrustAllManager.allowAllSsl(con);
                    con.setConnectTimeout(1000);
                    try {
                        con.connect();
                        log.info("External node " + getConfiguration().getHostname() + " is online");
                        log.info(Ansi.colorize("External server Startup OK for " + getConfiguration().getSource().get(0) + " of " + getHostname(),
                            RbelAnsiColors.GREEN_BOLD));
                        return true;
                    } catch (ConnectException | SocketTimeoutException cex) {
                        if (!quiet) {
                            log.info("No connection to " + url + " of " + getHostname() + "...");
                        }
                    } catch (SSLHandshakeException sslhe) {
                        log.warn(Ansi.colorize("SSL handshake but server at least seems to be up!" + sslhe.getMessage(),
                            RbelAnsiColors.YELLOW_BOLD));
                        return true;
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
                    return false;
                });
            return true;
        } catch (ConditionTimeoutException cte) {
            if (!quiet) {
                throw new TigerTestEnvException("Timeout waiting for external server to respond at '"
                    + getConfiguration().getExternalJarOptions().getHealthcheck() + "'!");
            }
        }
        return false;
    }

    URL getHealthcheckUrl() {
        try {
            return new URL(getConfiguration().getExternalJarOptions().getHealthcheck());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                "Could not build healthcheck URL from '" + getConfiguration().getExternalJarOptions().getHealthcheck()
                    + "'!", e);
        }
    }

    private boolean isHealthCheckNone() {
        return getConfiguration().getExternalJarOptions() == null ||
            getConfiguration().getExternalJarOptions().getHealthcheck() == null ||
            getConfiguration().getExternalJarOptions().getHealthcheck().equals("NONE");
    }
}
