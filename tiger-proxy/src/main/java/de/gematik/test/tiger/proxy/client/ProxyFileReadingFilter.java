/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import static de.gematik.test.tiger.proxy.AbstractTigerProxy.PAIRED_MESSAGE_UUID;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelJexlExecutor;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@RequiredArgsConstructor
@Slf4j
public class ProxyFileReadingFilter implements RbelMessagePostProcessor {

    private final RbelJexlExecutor jexlExecutor = new RbelJexlExecutor();
    private final String filterExpression;
    private final Map<String, RbelElement> deletedMessages = new HashMap<>();

    @Override
    public void performMessagePostConversionProcessing(RbelElement message, RbelConverter converter,
        JSONObject messageObject) {
        if (isKeepMessage(message)) {
            if (messageObject.has(PAIRED_MESSAGE_UUID)) {
                RbelElement deletedPartner = deletedMessages.get(messageObject.getString(PAIRED_MESSAGE_UUID));
                if (deletedPartner != null) {
                    deletedMessages.remove(deletedPartner.getUuid());
                }
            }
        } else {
            if (messageObject.has(PAIRED_MESSAGE_UUID)) {
                final var partnerMessage = deletedMessages.get(messageObject.getString(PAIRED_MESSAGE_UUID));
                if (partnerMessage != null) {
                    converter.removeMessage(message);
                    converter.removeMessage(partnerMessage);
                }
            } else {
                deletedMessages.put(message.getUuid(), message);
            }
        }
    }

    private boolean isKeepMessage(RbelElement message) {
        return jexlExecutor.matchesAsJexlExpression(message, filterExpression);
    }
}
