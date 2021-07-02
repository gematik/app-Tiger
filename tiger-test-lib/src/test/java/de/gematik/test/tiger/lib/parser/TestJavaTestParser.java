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
import de.gematik.test.tiger.lib.parser.model.Testcase;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestJavaTestParser {

    @Test
    @Afo("A_20315-01")
    void testParseTestSourcesOK() {
        final JavaTestParser parser = new JavaTestParser();

        parser.parseDirectory(Paths.get("src", "test").toFile());
        final Map<String, List<Testcase>> tcs = parser.getParsedTestcasesPerAfo();

        assertThat(tcs).containsOnlyKeys("A_20315-01");
        assertThat(tcs.get("A_20315-01")).hasSize(1);
        assertThat(tcs.get("A_20315-01").get(0).getMethod()).isEqualTo("testParseTestSourcesOK");
    }

}
