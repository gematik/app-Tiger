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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.gematik.test.tiger.testenvmgr.env.ScenarioReplayer;
import io.cucumber.plugin.event.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/replay")
@Slf4j
public class ReplayController {

  private final ScenarioReplayer scenarioReplayer;

  public ReplayController(@Autowired ScenarioReplayer scenarioReplayer) {
    this.scenarioReplayer = scenarioReplayer;
  }

  @PostMapping
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  public void replayScenario(@Valid @RequestBody ScenarioIdentifier scenarioIdentifier) {
    scenarioReplayer.rerunTest(scenarioIdentifier);
  }

  @ExceptionHandler(value = NoSuchElementException.class)
  public ResponseEntity<String> handleNotFound() {
    return ResponseEntity.notFound().build();
  }

  @Data
  public static class ScenarioIdentifier {

    private final URI scenarioUri;
    private final Location location;
    private final int variantIndex;

    @JsonCreator
    public ScenarioIdentifier(
        @JsonProperty("scenarioUri") @NotNull URI scenarioUri,
        @JsonProperty("location") @NotNull Map<String, Integer> location,
        @JsonProperty("variantIndex") @NotNull Integer variantIndex) {
      this.scenarioUri = scenarioUri;
      this.location =
          location == null ? null : new Location(location.get("line"), location.get("column"));
      this.variantIndex = variantIndex;
    }

    public boolean isScenarioOutline() {
      return variantIndex >= 0;
    }
  }
}
