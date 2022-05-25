/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class MessageMetaDataDto {

    String uuid;
    String path;
    String method;
    Integer responseCode;
    String recipient;
    String sender;
    long sequenceNumber;

    public static MessageMetaDataDto createFrom(RbelElement el) {
        MessageMetaDataDto.MessageMetaDataDtoBuilder b = MessageMetaDataDto.builder();
        b = b.uuid(el.getUuid())
            .sequenceNumber(getElementSequenceNumber(el))
            .sender(el.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getSender)
                .filter(Objects::nonNull)
                .filter(element -> element.getRawStringContent() != null)
                .flatMap(element -> Optional.of(element.getRawStringContent()))
                .orElse(""))
            .recipient(el.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getReceiver)
                .filter(Objects::nonNull)
                .filter(element -> element.getRawStringContent() != null)
                .flatMap(element -> Optional.of(element.getRawStringContent()))
                .orElse(""));

        if (el.hasFacet(RbelHttpRequestFacet.class)) {
            RbelHttpRequestFacet req = el.getFacetOrFail(RbelHttpRequestFacet.class);
            b = b.path(req.getPath().getRawStringContent())
                .method(req.getMethod().getRawStringContent())
                .responseCode(null);
        } else if (el.hasFacet(RbelHttpResponseFacet.class)) {
            b.responseCode(Integer.parseInt(el.getFacetOrFail(RbelHttpResponseFacet.class)
                .getResponseCode().getRawStringContent()));
        } else {
            throw new IllegalArgumentException(
                "We do not support meta data for non http elements (" + el.getClass().getName() + ")");
        }
        return b.build();
    }

    private static long getElementSequenceNumber(RbelElement rbelElement) {
        return rbelElement.getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getSequenceNumber)
            .orElse(0L);
    }
}
