/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelBinaryFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.BinaryExchangeDescriptor;

@Builder
@Data
@Slf4j
public class BinaryExchangeHandler implements Consumer<BinaryExchangeDescriptor> {

    private final RbelLogger rbelLogger;
    private final TigerProxy tigerProxy;

    @Override
    public void accept(BinaryExchangeDescriptor info) {
        try {
            log.trace("Finalizing binary exchange...");
            final RbelElement request = getRbelLogger().getRbelConverter()
                .parseMessage(info.getBinaryRequest().getBytes(), toRbelHostname(info.getClientAddress()),
                    toRbelHostname(info.getServerAddress()), Optional.empty());
            final RbelElement response = getRbelLogger().getRbelConverter()
                .parseMessage(info.getBinaryResponse().getBytes(), toRbelHostname(info.getServerAddress()),
                    toRbelHostname(info.getClientAddress()), Optional.empty());
            request.addFacet(RbelMessageTimingFacet.builder()
                .transmissionTime(info.getBinaryRequest().getTimestamp().atZone(ZoneId.systemDefault()))
                .build());
            response.addFacet(RbelMessageTimingFacet.builder()
                .transmissionTime(info.getBinaryResponse().getTimestamp().atZone(ZoneId.systemDefault()))
                .build());
            request.addFacet(new RbelBinaryFacet());
            response.addFacet(new RbelBinaryFacet());
            log.trace("Finalized binary exchange from {} to {}", request.getFacet(RbelTcpIpMessageFacet.class)
                    .map(RbelTcpIpMessageFacet::getSenderHostname)
                    .map(Objects::toString)
                    .orElse(""),
                response.getFacet(RbelTcpIpMessageFacet.class)
                    .map(RbelTcpIpMessageFacet::getSenderHostname)
                    .map(Objects::toString)
                    .orElse(""));
        } catch (RuntimeException e) {
            log.warn("Uncaught exception during handling of request", e);
            propagateExceptionMessageSafe(e);
            throw e;
        }
    }

    private void propagateExceptionMessageSafe(RuntimeException exception) {
        try {
            tigerProxy.propagateException(exception);
        } catch (Exception handlingException) {
            log.warn("While propagating an exception another error occured (ignoring):", handlingException);
        }
    }

    private RbelHostname toRbelHostname(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            return RbelHostname.builder()
                .hostname(((InetSocketAddress) socketAddress).getHostName())
                .port(((InetSocketAddress) socketAddress).getPort())
                .build();
        } else {
            log.warn("Incompatible socketAddress encountered: " + socketAddress.getClass().getSimpleName());
            return null;
        }
    }
}
