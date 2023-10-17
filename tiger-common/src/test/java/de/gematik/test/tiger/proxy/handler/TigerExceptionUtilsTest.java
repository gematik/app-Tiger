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

package de.gematik.test.tiger.proxy.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import org.junit.jupiter.api.Test;

class TigerExceptionUtilsTest {

    @Test
    public void standardCase() {
        assertThat(TigerExceptionUtils.getCauseWithType(new RuntimeException("blub", new SocketException("root cause message")), SocketException.class))
            .get()
            .hasSameClassAs(new SocketException())
            .matches(e -> e.getMessage().equals("root cause message"));
    }

    @Test
    public void flatExceptionCase() {
        assertThat(TigerExceptionUtils.getCauseWithType(new SocketException("flat cause message"), SocketException.class))
            .get()
            .hasSameClassAs(new SocketException())
            .matches(e -> e.getMessage().equals("flat cause message"));
    }

    @Test
    public void couldNotMatchException_rootCausePresent() {
        assertThat(TigerExceptionUtils.getCauseWithType(new RuntimeException("blub", new SocketException("root cause message")), FileNotFoundException.class))
            .isEmpty();
    }

    @Test
    public void couldNotMatchException_noRootCausePresent() {
        assertThat(TigerExceptionUtils.getCauseWithType(new RuntimeException("blub"), IOException.class))
            .isEmpty();
    }

    @Test
    public void nullCase() {
        assertThat(TigerExceptionUtils.getCauseWithType(null, IOException.class))
            .isEmpty();
    }
}
