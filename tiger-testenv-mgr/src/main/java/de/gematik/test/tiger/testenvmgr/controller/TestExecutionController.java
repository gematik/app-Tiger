/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
