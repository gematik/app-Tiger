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

package de.gematik.test.tiger.admin.bdd.actions.lolevel;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static org.awaitility.Awaitility.await;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.thucydides.core.annotations.Step;

public class Pause implements Task {

    private final int waitms;

    public Pause(int waitms) {
        this.waitms = waitms;
    }

    public static Pause pauseFor(int waitms) {
        return instrumented(Pause.class, waitms);
    }

    @Override
    @Step("{0} pauses #waitms ms")
    public <T extends Actor> void performAs(T actor) {
        final long startms = System.currentTimeMillis();
        await().until(() -> System.currentTimeMillis() - startms - waitms >= 0);
    }
}
