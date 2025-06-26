/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.glue;

import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.annotations.Steps;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;

@Slf4j
public class TestGlue {

  @Steps private TigerGlue tigerGlue;

  @When("a step calls a substep of level {int}")
  public void aStepCallsASubstepOfLevel(Integer level) {
    tigerGlue.tgrShowColoredText("blue", "Performing substep of level " + level);
    if (level > 1) {
      new Actor("A").attemptsTo(new SubstepTask().withLevel(level - 1));
    }
  }

  public static class SubstepTask implements Task {
    @Steps private TigerGlue tigerGlue;

    private int level;

    public SubstepTask withLevel(int level) {
      this.level = level;
      return this;
    }

    @Override
    public <T extends Actor> void performAs(T actor) {
      tigerGlue.tgrShowColoredText("blue", "Performing substep of level " + level);
      if (level > 1) {
        actor.attemptsTo(new SubstepTask().withLevel(level - 1));
      }
    }
  }
}
