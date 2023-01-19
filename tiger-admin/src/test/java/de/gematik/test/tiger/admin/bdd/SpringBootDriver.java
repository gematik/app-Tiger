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

package de.gematik.test.tiger.admin.bdd;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"./src/test/resources/features"},
    plugin = {"de.gematik.test.tiger.admin.bdd.SpringBootStarterPlugin"},
    glue = {"de.gematik.test.tiger.admin.bdd.steps"})
@SpringBootTest()
public class SpringBootDriver {

    @LocalServerPort
    public static int adminPort;

    public static int getAdminPort() {
        return adminPort == 0 ? 8080 : adminPort;
    }
}
