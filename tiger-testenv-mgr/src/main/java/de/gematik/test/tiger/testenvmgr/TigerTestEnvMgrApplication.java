/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
import jakarta.servlet.ServletContextListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    scanBasePackageClasses = {TigerBuildPropertiesService.class, TigerTestEnvMgrApplication.class})
@Slf4j
public class TigerTestEnvMgrApplication implements ServletContextListener {

  public static void main(String[] args) {
    new SpringApplicationBuilder()
        .bannerMode(Mode.OFF)
        .sources(TigerTestEnvMgrApplication.class)
        .initializers()
        .run(args);
  }

  @Bean
  public TigerTestEnvMgr tigerTestEnvMgr() {
    TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
    log.info(
        Ansi.colorize("Tiger standalone test environment manager UP!", RbelAnsiColors.GREEN_BOLD));
    return envMgr;
  }
}
