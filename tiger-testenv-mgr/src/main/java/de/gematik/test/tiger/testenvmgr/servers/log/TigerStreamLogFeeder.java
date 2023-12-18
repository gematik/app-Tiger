/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public class TigerStreamLogFeeder {
  public TigerStreamLogFeeder(
      String serverId, Logger log, InputStream inputStream, Level logLevel) {
    new Thread(
            () -> {
              try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line = reader.readLine();
                while (line != null) {
                  if (logLevel == Level.ERROR) {
                    log.error(line);
                  } else {
                    log.info(line);
                  }
                  line = reader.readLine();
                }
              } catch (IOException e) {
                log.error(
                    "Error while reading log from input stream for server '" + serverId + "'", e);
              }
            })
        .start();
  }
}
