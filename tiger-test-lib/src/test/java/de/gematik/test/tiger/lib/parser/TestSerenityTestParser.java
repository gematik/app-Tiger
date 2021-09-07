/*
 * Copyright (c) 2021 gematik GmbH
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

package de.gematik.test.tiger.lib.parser;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.lib.parser.model.TestResult;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TestSerenityTestParser {

    @Test
    public void parseSerenityResultsOK() {
        final SerenityTestResultParser parser = new SerenityTestResultParser();

        final Map<String, TestResult> results = new HashMap<>();
        parser.parseDirectoryForResults(results, Paths.get("src", "test", "resources", "testdata", "parser","bdd").toFile());

        assertThat(results).containsOnlyKeys(
            "fordere-access-token-mittels-sso-token-an:gettoken-mit-sso-token---veralteter-sso-token-wird-abgelehnt",
            "fordere-access-token-mit-einer-signierten-challenge-an:gettoken-signierte-challenge---veralteter-token-code-wird-abgelehnt");
    }

    @Test
    public void parseSerenityResultsInvalidRoot() {
        final SerenityTestResultParser parser = new SerenityTestResultParser();

        final Map<String, TestResult> results = new HashMap<>();
        parser.parseDirectoryForResults(results, Paths.get("src", "test", "resources", "bdd-NonExisting").toFile());

        assertThat(results.keySet()).hasSize(0);
    }
}
