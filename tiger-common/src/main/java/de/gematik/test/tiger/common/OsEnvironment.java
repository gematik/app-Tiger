/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import de.gematik.test.tiger.common.exceptions.TigerOsException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OsEnvironment {

    public static String getAsString(String name) {
        return Optional.ofNullable(System.getenv(name)).orElse(System.getProperty(name));
    }

    public static String getAsString(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name)).orElse(System.getProperty(name, defaultValue));
    }

    public static boolean getAsBoolean(String name) {
        var val = getAsString(name);
        return val != null && val.equals("1");
    }

    public static boolean isIPv4(String ipAddress) {
        boolean isIPv4 = false;

        if (ipAddress != null) {
            try {
                InetAddress inetAddress = InetAddress.getByName(ipAddress);
                isIPv4 = (inetAddress instanceof Inet4Address) && inetAddress.getHostAddress().equals(ipAddress);
            } catch (UnknownHostException ignored) {
            }
        }

        return isIPv4;
    }

    public static String getDockerHostIp() {
        String dockerNet;
        if (SystemUtils.IS_OS_LINUX) {
            dockerNet = "docker0";
        } else if (SystemUtils.IS_OS_MAC) {
            dockerNet = "en0";
        } else if (SystemUtils.IS_OS_WINDOWS) {
            // TODO TGR-248 dynamically detect network once i know how to do that, check with testcontaienrs 1.16.0 release
            log.warn("Docker host IP detection on Windows is not supported at the moment, "
                + "falling back to default docker host ip 172.17.0.1 - FINGERS CROSSED!");
            return "172.17.0.1";
        } else {
            throw new TigerOsException("Docker host ip detection is only supported on Linux, Mac, Windows! "
                + "Your system seems to be reported as " + getAsString("os.name"));
        }
        try {
            Optional<ArrayList<InetAddress>> optAdresses = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .filter(netint -> netint.getDisplayName().equals(dockerNet))
                .map(netint -> Collections.list(netint.getInetAddresses()))
                .findAny();

            if (optAdresses.isEmpty()) {
                log.warn("Unable to detect network '" + dockerNet + "' on this host, assuming default value 172.17.0.1");
                try {
                    Collections.list(NetworkInterface.getNetworkInterfaces()).forEach(
                        netinf -> log.warn("Found net interface " + netinf.getDisplayName())
                    );
                } catch (SocketException ex) {
                    log.error("Even unable to find any network interface!", ex);
                }
                return "172.17.0.1";
            } else {
                return optAdresses.get().stream()
                    .map(InetAddress::getHostAddress)
                    .filter(OsEnvironment::isIPv4)
                    .findAny().orElseThrow(() -> new TigerOsException("Docker network has no ipv4 address!"));
            }
        } catch (Exception e) {
            try {
                Collections.list(NetworkInterface.getNetworkInterfaces()).forEach(
                    netinf -> log.warn("Found net interface " + netinf.getDisplayName())
                );
            } catch (SocketException ex) {
                log.error("Even unable to find any network interface!", ex);
            }
            throw new TigerOsException("Unable to detect docker network!", e);
        }
    }
}
