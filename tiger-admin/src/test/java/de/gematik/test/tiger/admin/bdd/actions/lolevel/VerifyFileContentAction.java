package de.gematik.test.tiger.admin.bdd.actions.lolevel;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import lombok.SneakyThrows;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.ensure.Ensure;


public class VerifyFileContentAction implements Performable {

    private final String fileName;
    private final String expectedFileContent;

    public VerifyFileContentAction(String fileName, String expectedFileContent) {
        this.fileName = fileName;
        this.expectedFileContent = expectedFileContent;
    }

    public static VerifyFileContentAction ofTypeVia(String fileName, String expectedFileContent) {
        return instrumented(VerifyFileContentAction.class, fileName, expectedFileContent);
    }

    @SneakyThrows
    @Override
    public <T extends Actor> void performAs(T t) {
        String filePath = theActorInTheSpotlight().recall("filepath");
        String fullFilePath = filePath + File.separator + fileName;
        BufferedReader bufferedReader = new BufferedReader(new FileReader(fullFilePath));
        String[] expectedLines = expectedFileContent.split("\\r?\\n");
        while (bufferedReader.ready()) {
            for (String expectedLine : expectedLines) {
                String actualLine = bufferedReader.readLine().trim();
                t.attemptsTo(
                    Ensure.that(expectedLine.trim()).isEqualTo(actualLine));
            }
        }
    }
}
