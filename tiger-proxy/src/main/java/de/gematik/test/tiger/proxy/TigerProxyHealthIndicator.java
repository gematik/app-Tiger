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

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;
import lombok.AllArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("messageQueue")
@AllArgsConstructor
public class TigerProxyHealthIndicator implements HealthIndicator {
    private final TigerProxy tigerProxy;

    @Override
    public Health health() {

        long bufferSize = tigerProxy.getRbelLogger().getMessageHistory().stream()
            .map(RbelElement::getRawContent).mapToLong((rawContent) -> (long) rawContent.length).sum();
        return Health.up()
            .withDetail("rbelMessages", tigerProxy.getRbelMessages().size())
            .withDetail("rbelMessageBuffer", bufferSize)
            .build();
    }
}
