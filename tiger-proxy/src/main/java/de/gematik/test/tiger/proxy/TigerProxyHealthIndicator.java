/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

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
        long bufferSize = tigerProxy.getRbelLogger().getRbelConverter().getCurrentBufferSize();
        return Health.up()
            .withDetail("rbelMessages", tigerProxy.getRbelLogger().getMessageHistory().size())
            .withDetail("rbelMessageBuffer", bufferSize)
            .build();
    }
}
