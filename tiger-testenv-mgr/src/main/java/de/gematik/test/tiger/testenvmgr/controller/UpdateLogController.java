/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.TigerServerLogDto;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogUpdate;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateLogController implements TigerServerLogListener {

    public final SimpMessagingTemplate template;
    public final TigerTestEnvMgr tigerTestEnvMgr;

    @PostConstruct
    public void addWebSocketListener() {
        tigerTestEnvMgr.getServers().values().forEach(server -> server.registerLogListener(this));
    }

    @Override
    public void receiveServerLogUpdate(TigerServerLogUpdate update) {
        log.trace("Propagating tiger server log udpate {}", update);
        template.convertAndSend("/topic/serverLog", TigerServerLogDto.createFrom(update));
    }
}
