/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.env.TestEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePushController implements TigerUpdateListener {

    public final SimpMessagingTemplate template;
    public final TigerTestEnvMgr tigerTestEnvMgr;

    @PostConstruct
    public void addWebSocketListener() {
        tigerTestEnvMgr.registerNewListener(this);
    }

    @Override
    public void receiveTestEnvUpdate(TigerStatusUpdate update) {
        log.info("Propagating status udpate {}", update);
        template.convertAndSend("/topic/envStatus", TestEnvStatusDto.createFrom(update));
    }
}
