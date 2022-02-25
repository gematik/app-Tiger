/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
    @Step("{0} shows tab #tabName")
    public <T extends Actor> void performAs(T actor) {
        final long startms = System.currentTimeMillis();
        await().until(() -> System.currentTimeMillis() - startms - waitms >= 0);
    }
}
