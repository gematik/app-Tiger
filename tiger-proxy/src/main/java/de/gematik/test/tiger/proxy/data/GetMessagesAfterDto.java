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
public class GetMessagesAfterDto {
    String lastMsgUuid;
    List<HtmlMessage> htmlMsgList;
    List<MessageMetaDataDto> metaMsgList;
    Integer pagesAvailable;
    Integer totalMsgCount;
}
