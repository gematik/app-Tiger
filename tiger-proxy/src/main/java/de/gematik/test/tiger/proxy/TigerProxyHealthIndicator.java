/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
