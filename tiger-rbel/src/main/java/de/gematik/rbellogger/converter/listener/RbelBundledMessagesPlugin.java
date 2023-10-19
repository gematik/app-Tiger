/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter.listener;

import de.gematik.rbellogger.converter.RbelBundleCriterion;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet.RbelHostnameFacetBuilder;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelJexlExecutor;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

public class RbelBundledMessagesPlugin implements RbelConverterPlugin {

    private final RbelJexlExecutor executor = new RbelJexlExecutor();

    @Override
    public void consumeElement(RbelElement messageElement, RbelConverter converter) {
        if (!messageElement.hasFacet(RbelTcpIpMessageFacet.class)) {
            return;
        }

        for (RbelBundleCriterion bundleCriterion : converter.getBundleCriterionList()) {
            String bundledServerName = bundleCriterion.getBundledServerName();
            if (StringUtils.isEmpty(bundledServerName)) {
                continue;
            }

            checkJexlExpressionsAndAddBundledServernameIfPossible(messageElement, RbelTcpIpMessageFacet::getReceiver,
                bundleCriterion.getReceiver(), bundledServerName);

            checkJexlExpressionsAndAddBundledServernameIfPossible(messageElement, RbelTcpIpMessageFacet::getSender,
                bundleCriterion.getSender(), bundledServerName);

            if (messageElement.hasFacet(RbelHttpResponseFacet.class)) {
                copyAndAddBundledServernameIfPossible(messageElement);
            }
        }
    }

    private void checkJexlExpressionsAndAddBundledServernameIfPossible(RbelElement message,
                                                                       Function<RbelTcpIpMessageFacet, RbelElement> hostnameExtractor,
                                                                       List<String> jexlExpressionList, String bundledServerName) {
        if (jexlExpressionList == null) {
            return;
        }

        for (String jexlExpression : jexlExpressionList) {
            if (executor.matchesAsJexlExpression(message, jexlExpression, Optional.empty())) {
                message.getFacet(RbelTcpIpMessageFacet.class)
                    .map(hostnameExtractor)
                        .ifPresent(hostnameElement -> changeHostnameFacet(hostnameElement, bundledServerName));
            }
        }
    }

    private void changeHostnameFacet(RbelElement hostnameElement, String bundledServerName) {
        RbelHostnameFacetBuilder hostnameFacetBuilder = hostnameElement.getFacet(RbelHostnameFacet.class)
            .map(RbelHostnameFacet::toBuilder)
            .orElse(RbelHostnameFacet.builder());

        RbelHostnameFacet hostnameFacet = hostnameFacetBuilder
            .bundledServerName(Optional.ofNullable(RbelElement.wrap(hostnameElement, bundledServerName)))
            .build();

        hostnameElement.addOrReplaceFacet(hostnameFacet);
    }

    private void copyAndAddBundledServernameIfPossible(RbelElement message) {
        copyBundledServerNameFromTo(message, RbelTcpIpMessageFacet::getSender, RbelTcpIpMessageFacet::getReceiver);
        copyBundledServerNameFromTo(message, RbelTcpIpMessageFacet::getReceiver, RbelTcpIpMessageFacet::getSender);
    }

    private void copyBundledServerNameFromTo(RbelElement rbelElement,
                                             Function<RbelTcpIpMessageFacet, RbelElement> sourceExtractor,
                                             Function<RbelTcpIpMessageFacet, RbelElement> destinationExtractor) {
        rbelElement.getFacet(RbelTcpIpMessageFacet.class)
            .map(sourceExtractor)
            .ifPresent(hostname -> rbelElement.getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getRequest)
                .flatMap(el -> el.getFacet(RbelTcpIpMessageFacet.class))
                .map(destinationExtractor)
                .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
                .flatMap(e -> e.getBundledServerName())
                .flatMap(el -> el.seekValue(String.class))
                .ifPresent(bundledServerName -> changeHostnameFacet(hostname, bundledServerName)));
    }
}
