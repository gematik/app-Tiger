/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessageMetaDataDto {
    String uuid;
    String path;
    String method;
    int status;
    List<String> headers;
    String recipient;
    String sender;
    long sequenceNumber;

    long timestamp;
}
