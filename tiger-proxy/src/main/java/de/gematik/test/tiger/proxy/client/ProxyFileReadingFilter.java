/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import static de.gematik.test.tiger.proxy.AbstractTigerProxy.PAIRED_MESSAGE_UUID;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;

@RequiredArgsConstructor
public class ProxyFileReadingFilter implements RbelMessagePostProcessor {

    private final RbelJexlExecutor jexlExecutor = new RbelJexlExecutor();
    private final String filterExpression;
    private final Map<String, RbelElement> deletedMessages = new HashMap<>();
    private final Set<String> addedMessages = new HashSet<>();

    @Override
    public void performMessagePostConversionProcessing(RbelElement message, RbelConverter converter,
        JSONObject messageObject) {
        final boolean keepMessage = jexlExecutor.matchesAsJexlExpression(message, filterExpression);
        if (!keepMessage) {
            if (!messageObject.has(PAIRED_MESSAGE_UUID)
                || !addedMessages.contains(messageObject.getString(PAIRED_MESSAGE_UUID))) {
                    deletedMessages.put(message.getUuid(), message);
                    converter.removeMessage(message);
                }
        } else {
            if (messageObject.has(PAIRED_MESSAGE_UUID)) {
                RbelElement deletedPartner = deletedMessages.get(messageObject.getString(PAIRED_MESSAGE_UUID));
                if (deletedPartner != null) {
                    converter.removeMessage(message);
                    converter.addMessage(deletedPartner);
                    converter.addMessage(message);
                }
            } else {
                addedMessages.add(message.getUuid());
            }
        }
    }
}
