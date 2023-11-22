/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr.servers.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public class TigerStreamLogFeeder {
  public TigerStreamLogFeeder(Logger log, InputStream inputStream, Level logLevel) {
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
                throw new RuntimeException(e);
              }
            })
        .start();
  }
}
