package de.gematik.test.tiger.admin.bdd.actions;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;

public class AddServer implements Performable {

    public static Target BTN_ADD_SERVER_ON_WELCOME_CARD = Target.the("add server button on welcome card")
        .locatedBy(".server-content .btn.btn-add-server");
    public static Target BTN_ADD_SERVER_ON_SIDEBAR = Target.the("add server button in sidebar")
        .locatedBy(".sidebar-toolbar .btn.btn-add-server");
    public static Target MODAL_ADD_SERVER = Target.the("modal to add server")
        .locatedBy("#add-server-modal");
    public static Target BTN_ADD_SERVER_OK = Target.the("ok button to add server")
        .locatedBy(".btn-add-server-ok");
    String nodeType;
    Target addServerBtn;

    public AddServer(String nodeType, Target addServerBtn) {
        this.nodeType = nodeType;
        this.addServerBtn = addServerBtn;
    }

    public static Target getNodeTypeListEntryFor(String nodeType) {
        return Target.the("entry for " + nodeType)
            .locatedBy("//div[@id='add-server-modal']//li[contains(@class, 'list-group-item') and text()='" + nodeType + "']");
    }

    public static AddServer ofTypeVia(String nodeType, Target addServerBtn) {
        return instrumented(AddServer.class, nodeType, addServerBtn);
    }

    @Override
    @Step("{0} adds a new node #nodeType via #addServerBtn")
    public <T extends Actor> void performAs(T t) {
        AdminHomePage homePage = new AdminHomePage();
        int nodeCount = homePage.getNodeCount();
        if (nodeCount == 0) {
            nodeCount++;
        }
        t.attemptsTo(
            // Actions
            Ensure.that(addServerBtn).isEnabled(),
            Click.on(addServerBtn),
            Ensure.that(MODAL_ADD_SERVER).isDisplayed(),
            Click.on(getNodeTypeListEntryFor(nodeType)),
            Click.on(BTN_ADD_SERVER_OK),
            // Verification
            Ensure.that(SnackActions.getSnackWithTextStartingWith("Added node ")).isDisplayed(),
            SnackActions.closeSnack(),
            Ensure.that(AdminHomePage.theNumberOfNodes()).isEqualTo(nodeCount + 1),
            Ensure.that(AdminHomePage.theLastFormularType()).isEqualTo(nodeType)
        );
    }
}
