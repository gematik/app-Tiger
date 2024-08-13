/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr.controller;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/testExecution")
@Slf4j
public class TestExecutionController {
  @Setter private Runnable shutdownListener = () -> {};
  @Setter private Runnable pauseListener = () -> {};

  @PutMapping(path = "/quit")
  public void quit() {
    log.trace("Fetch request to quit() received");
    new Thread(shutdownListener).start();
  }

  @PutMapping(path = "/pause")
  public void pause() {
    log.trace("Fetch request to pause() received");
    new Thread(pauseListener).start();
  }
}
