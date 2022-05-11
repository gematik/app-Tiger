/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class TigerTestEnvMgrApplication implements ServletContextListener {

    private TigerTestEnvMgr testEnvMgr;

    public static void main(String[] args) {
        SpringApplication.run(TigerTestEnvMgrApplication.class, args);
    }

    @Bean
    public TigerTestEnvMgr tigerTestEnvMgr() {
        TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        log.info(Ansi.colorize("Tiger standalone test environment UP!", RbelAnsiColors.GREEN_BOLD));
        this.testEnvMgr = envMgr;
        return envMgr;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Initiating testenv-mgr shutdown...");
        if (testEnvMgr != null) {
            testEnvMgr.shutDown();
        }
    }
}
