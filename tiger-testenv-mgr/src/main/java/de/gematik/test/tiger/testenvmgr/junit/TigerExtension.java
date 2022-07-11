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

package de.gematik.test.tiger.testenvmgr.junit;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgrApplication;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.*;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
public class TigerExtension implements BeforeTestExecutionCallback, ParameterResolver, AfterTestExecutionCallback {

    private TigerTestEnvMgr tigerTestEnvMgr;
    private ConfigurableApplicationContext envMgrApplicationContext;

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        assertInitialized(context);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (tigerTestEnvMgr != null) {
            log.info("After test execution - tearing down context");
            if (!TigerGlobalConfiguration.readBoolean("tiger.skipEnvironmentSetup", false)) {
                log.info("Stopping Test-Env");
                tigerTestEnvMgr.shutDown();
            }
            envMgrApplicationContext.close();
            TigerGlobalConfiguration.reset();
            tigerTestEnvMgr = null;
        }
    }

    private TigerTest findTigerAnnotation(ExtensionContext context) {
        return context.getTestMethod().get().getAnnotation(TigerTest.class);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
        return parameterContext.getParameter().getType().isAssignableFrom(TigerTestEnvMgr.class) ||
            parameterContext.getParameter().getType().isAssignableFrom(UnirestInstance.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
        assertInitialized(extensionContext);
        if (parameterContext.getParameter().getType().isAssignableFrom(TigerTestEnvMgr.class)) {
            return this.tigerTestEnvMgr;
        } else if (parameterContext.getParameter().getType().isAssignableFrom(UnirestInstance.class)) {
            final UnirestInstance unirestInstance = Unirest.spawnInstance();
            unirestInstance.config().proxy("127.0.0.1", tigerTestEnvMgr.getLocalTigerProxy().getProxyPort());
            return unirestInstance;
        } else {
            throw new RuntimeException("Could not instantiate parameter, unsupported typ "
                + parameterContext.getParameter().getType());
        }
    }

    private void assertInitialized(ExtensionContext extensionContext) {
        if (tigerTestEnvMgr == null) {
            buildNewTigerTestEnvMgr(extensionContext);
        }
    }

    private void buildNewTigerTestEnvMgr(ExtensionContext extensionContext) {
        log.info("TigerTest entering setup");
        TigerGlobalConfiguration.reset();
        final TigerTest tigerAnnotation = findTigerAnnotation(extensionContext);
        Map<String, String> additionalProperties = new HashMap<>();
        if (StringUtils.isEmpty(tigerAnnotation.cfgFilePath())) {
            TigerGlobalConfiguration.setRequireTigerYaml(false);
        } else {
            additionalProperties.put("TIGER_TESTENV_CFGFILE", tigerAnnotation.cfgFilePath());
        }
        if (StringUtils.isNotEmpty(tigerAnnotation.tigerYaml())) {
            additionalProperties.put("TIGER_YAML", tigerAnnotation.tigerYaml());
            TigerGlobalConfiguration.setRequireTigerYaml(false);
        }
        additionalProperties.put("tiger.skipEnvironmentSetup",
            Boolean.toString(tigerAnnotation.skipEnvironmentSetup()));
        if (tigerAnnotation.additionalProperties() != null) {
            Arrays.stream(tigerAnnotation.additionalProperties())
                .map(str -> str.split("="))
                .forEach(split -> additionalProperties.put(split[0].trim(), split[1].trim()));
        }
        log.debug("Initializing configuration with {}", additionalProperties);
        TigerGlobalConfiguration.initializeWithCliProperties(additionalProperties);

        envMgrApplicationContext = new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(Map.of("server.port",
                TigerGlobalConfiguration.readIntegerOptional("tiger.internal.testenvmgr.port").orElse(0)))
            .sources(TigerTestEnvMgrApplication.class)
            .web(WebApplicationType.SERVLET)
            .initializers()
            .run();

        tigerTestEnvMgr = envMgrApplicationContext.getBean(TigerTestEnvMgr.class);
        if (!TigerGlobalConfiguration.readBoolean("tiger.skipEnvironmentSetup", false)) {
            log.info("Starting Test-Env setup");
            tigerTestEnvMgr.setUpEnvironment();
        }
        log.info("TigerTest initialized, commencing actual test");
    }
}
