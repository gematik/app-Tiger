/*
 * Copyright (c) 2022 gematik GmbH
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
