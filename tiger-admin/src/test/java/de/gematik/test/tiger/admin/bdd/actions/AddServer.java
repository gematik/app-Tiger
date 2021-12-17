package de.gematik.test.tiger.admin.bdd.actions;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.PerformActionsOnSnack;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;

public class AddServer implements Performable {

    public static final Target BTN_ADD_SERVER_ON_WELCOME_CARD = Target.the("add server button on welcome card")
        .locatedBy(".server-content .btn.btn-add-server");
    public static final Target BTN_ADD_SERVER_ON_SIDEBAR = Target.the("add server button in sidebar")
        .locatedBy(".sidebar-toolbar .btn.btn-add-server");
    private static final Target MODAL_ADD_SERVER = Target.the("modal to add server")
        .locatedBy("#add-server-modal");
    private static final Target BTN_ADD_SERVER_OK = Target.the("ok button to add server")
        .locatedBy(".btn-add-server-ok");

    private final String nodeType;
    private final Target addServerBtn;

    public AddServer(String nodeType, Target addServerBtn) {
        this.nodeType = nodeType;
        this.addServerBtn = addServerBtn;
    }

    public static AddServer ofTypeVia(String nodeType, Target addServerBtn) {
        return instrumented(AddServer.class, nodeType, addServerBtn);
    }

    private static Target getNodeTypeListEntryFor(String nodeType) {
        return Target.the("entry for " + nodeType)
            .locatedBy("//div[@id='add-server-modal']"
                + "//li[contains(@class, 'list-group-item') and text()='" + nodeType + "']");
    }

    @Override
    @Step("{0} adds a new node #nodeType via #addServerBtn")
    public <T extends Actor> void performAs(T t) {
        int nodeCount = t.asksFor(AdminHomePage.theNumberOfNodes());
        if (nodeCount == 0) {
            nodeCount++;
        }
        t.attemptsTo(
            // Precondition
            Ensure.that(addServerBtn).isEnabled(),
            // Actions
            Click.on(addServerBtn),
            Ensure.that(MODAL_ADD_SERVER).isDisplayed(),
            Click.on(getNodeTypeListEntryFor(nodeType)),
            Click.on(BTN_ADD_SERVER_OK),
            // Verification
            Ensure.that(PerformActionsOnSnack.snackWithTextStartingWith("Added node ")).isDisplayed(),
            PerformActionsOnSnack.closeSnack(),
            Ensure.that(AdminHomePage.theNumberOfNodes()).isEqualTo(nodeCount + 1),
            Ensure.that(AdminHomePage.theLastFormularType()).isEqualTo(nodeType)
        );
    }
}
