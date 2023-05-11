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

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
class RbelMessageValidatorReadFileTest {

    @BeforeEach
    @AfterEach
    public void reset() {
        ((Deque<RbelElement>) ReflectionTestUtils.getField(LocalProxyRbelMessageListener.class, "validatableRbelMessages"))
            .clear();
        TigerGlobalConfiguration.reset();
    }

    @Test
    void testReadTrafficFile() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersActive.yaml");
        executeWithSecureShutdown(() -> {
            TigerDirector.start();

            RbelMessageValidator.instance.readTgrFile("src/test/resources/testdata/rezepsFiltered.tgr");

            assertThat(LocalProxyRbelMessageListener.getValidatableRbelMessages()).hasSize(96);
        });
    }

    private void executeWithSecureShutdown(Runnable test) {
        try {
            test.run();
        } finally {
            TigerDirector.getTigerTestEnvMgr().shutDown();
        }
    }
}
