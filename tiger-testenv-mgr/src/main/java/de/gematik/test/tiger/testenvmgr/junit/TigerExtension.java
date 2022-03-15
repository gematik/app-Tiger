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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.*;

@Slf4j
public class TigerExtension implements BeforeTestExecutionCallback, ParameterResolver, AfterTestExecutionCallback {

    private TigerTestEnvMgr tigerTestEnvMgr;

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        assertInitialized(context);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        if (tigerTestEnvMgr != null) {
            log.info("After test execution - tearing down context");
            tigerTestEnvMgr.shutDown();
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
        return parameterContext.getParameter().getType().isAssignableFrom(TigerTestEnvMgr.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
        assertInitialized(extensionContext);
        return this.tigerTestEnvMgr;
    }

    private void assertInitialized(ExtensionContext extensionContext) {
        if (tigerTestEnvMgr == null) {
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
            if (tigerAnnotation.additionalProperties() != null) {
                Arrays.stream(tigerAnnotation.additionalProperties())
                    .map(str -> str.split("="))
                    .forEach(split -> additionalProperties.put(split[0].trim(), split[1].trim()));
            }
            TigerGlobalConfiguration.initializeWithCliProperties(additionalProperties);
            tigerTestEnvMgr = new TigerTestEnvMgr();

            if (!tigerAnnotation.skipEnvironmentSetup()) {
                tigerTestEnvMgr.setUpEnvironment();
            }
        }
    }
}
