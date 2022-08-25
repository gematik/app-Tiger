/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("messageQueue")
public class TigerProxyHealthIndicator implements HealthIndicator {

    @Autowired
    TigerProxyReference proxyReference;

    @Override
    public Health health() {

        long bufferSize = proxyReference.getProxy().getRbelLogger().getMessageHistory().stream()
            .map(RbelElement::getRawContent).mapToLong((rawContent) -> {
                return (long) rawContent.length;
            }).sum();
        return Health.up()
            .withDetail("rbelMessages", proxyReference.getProxy().getRbelMessages().size())
            .withDetail("rbelMessageBuffer", bufferSize)
            .build();
    }
}
