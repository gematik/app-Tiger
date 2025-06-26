/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package org.apache.maven.surefire.junitplatform;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import java.util.Map;
import org.apache.maven.surefire.api.util.ScannerFilter;
import org.junit.platform.engine.Filter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestPlan;

/**
 * Tiger changes: - include the configuration parameters in the filter. These include cucumber
 * configuration keys which control the test execution. See {@link
 * TigerFailsafeProvider#newConfigurationParameters()}
 *
 * <p>This class is based on the JUnitPlatformProvider of the <a
 * href="https://github.com/apache/maven-surefire/blob/master/surefire-providers/surefire-junit-platform/src/main/java/org/apache/maven/surefire/junitplatform/">maven-surefire
 * project</a> (Apache License, Version 2.0 )
 */
final class TigerTestPlanScannerFilter implements ScannerFilter {

  private final Launcher launcher;

  private final Filter<?>[] includeAndExcludeFilters;
  private final Map<String, String> configurationParameters;

  TigerTestPlanScannerFilter(
      Launcher launcher,
      Filter<?>[] includeAndExcludeFilters,
      Map<String, String> configurationParameters) {
    this.launcher = launcher;
    this.includeAndExcludeFilters = includeAndExcludeFilters;
    this.configurationParameters = configurationParameters;
  }

  @Override
  public boolean accept(Class testClass) {
    LauncherDiscoveryRequest discoveryRequest =
        request()
            .selectors(selectClass(testClass.getName()))
            .filters(includeAndExcludeFilters)
            .configurationParameters(configurationParameters)
            .build();

    TestPlan testPlan = launcher.discover(discoveryRequest);

    return testPlan.containsTests();
  }
}
