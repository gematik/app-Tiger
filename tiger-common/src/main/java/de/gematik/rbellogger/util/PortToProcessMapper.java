/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.util.GlobalServerMap.mapPortToProcessIds;

import de.gematik.test.tiger.common.exceptions.TigerPortToProcessMappingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortToProcessMapper {

  private PortToProcessMapper() {
    throw new IllegalStateException("PortToProcessMapper class");
  }

  public static ConcurrentMap<Integer, Long> runPortMappingCommand(int port) {
    String os = System.getProperty("os.name").toLowerCase();
    String[] command = createCommand(os, port);
    Pattern pattern = createPattern(os);

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(executeProcess(command).getInputStream()))) {
      String line;

      while ((line = reader.readLine()) != null) {
        fillMapWithValues(pattern, line, port);
      }

      return GlobalServerMap.getPortToProcessId();
    } catch (IOException e) {
      throw new TigerPortToProcessMappingException(
          "Exception while trying execute command "
              + Arrays.toString(command)
              + "to map port to its process ids",
          e);
    }
  }

  private static Process executeProcess(String[] command) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    return processBuilder.start();
  }

  private static String[] createCommand(String os, int port) {
    if (os.contains("win")) {
      return new String[] {"cmd.exe", "/c", "netstat -ano | findstr :" + port};
    } else {
      return new String[] {
        "/bin/sh", "-c", "lsof -i -P -n | grep :" + port + " | awk '{print $9, $2}'"
      };
    }
  }

  private static Pattern createPattern(String os) {
    if (os.contains("win")) {
      return Pattern.compile(".*:(\\d+)\\s+.*:(\\d+)\\s+\\w+\\s+(\\d+)");
    } else if (os.contains("linux")) {
      return Pattern.compile(".*:(\\d+)->.*:(\\d+) (\\d+)");
    } else {
      return Pattern.compile("(.*):(\\d+)\\s+(\\d+)");
    }
  }

  private static void fillMapWithValues(Pattern pattern, String line, int port) {
    Matcher matcher = pattern.matcher(line);

    if (matcher.matches()) {
      String localPort = matcher.group(1);
      String remotePort = matcher.group(2);
      String processId = matcher.group(3);

      if (Integer.parseInt(localPort) == port
          || Integer.parseInt(remotePort) == port
              && GlobalServerMap.getProcessIdToBundledServerName()
                  .containsKey(Long.parseLong(processId))) {
        mapPortToProcessIds(Integer.parseInt(localPort), Long.parseLong(processId));
        mapPortToProcessIds(Integer.parseInt(remotePort), Long.parseLong(processId));
      }
    }
  }
}
