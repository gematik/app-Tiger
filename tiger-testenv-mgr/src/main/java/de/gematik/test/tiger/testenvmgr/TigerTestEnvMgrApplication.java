/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import javax.servlet.ServletContextListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class TigerTestEnvMgrApplication implements ServletContextListener {

    public static void main(String[] args) {
        SpringApplication.run(TigerTestEnvMgrApplication.class, args);
    }

    @Bean
    public TigerTestEnvMgr tigerTestEnvMgr() {
        TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        log.info(Ansi.colorize("Tiger standalone test environment UP!", RbelAnsiColors.GREEN_BOLD));
        return envMgr;
    }

}
