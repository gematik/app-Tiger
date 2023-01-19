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

package de.gematik.test.tiger.integration.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import de.gematik.test.tiger.lib.TigerDirector;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.rest.SerenityRest;

@Slf4j
public class TestTigerIntegrationExample {

    @When("User requests the startpage")
    public void userRequestsStartpage() {
        var rest = Unirest.spawnInstance();
        rest.config().proxy("127.0.0.1", TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail().getProxyPort());

        final HttpResponse<String> httpResponse = rest.get("http://winstone").asString();

        assertNotNull(httpResponse);
        assertEquals("Response code not 200", 200, httpResponse.getStatus());
    }

    @Then("User requests {string} with parameter {string}")
    public void user_requests_with_parameter(String path, String param) {
        SerenityRest.get("http://winstone" + path
            + "?" + param);
    }

    @Then("Hier erwarte ich einen Fehler")
    public void hier_erwarte_ich_einen_fehler() {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }
}
