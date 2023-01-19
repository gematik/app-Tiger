/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.common.data.config;

import java.util.List;
import lombok.Data;

@Data
public class CfgHelmChartOptions {
    private String context;
    private String podName;
    private String workingDir;
    private String nameSpace;
    private boolean debug = false;
    /** list of regex names for pods to be running to signal successful startup of helm chart **/
    private List<String> healthcheckPods;

    private List<String> values;
    /**
     * comma separated list of port forwardings
     * Entries can be either "podNameRegex:xxxx", which is shorthand for "podNameRegex:xxxx:xxxx or
     * "podNameRegex:xxxx:yyyy" where xxxx is the local port and yyyy is the port in the pod
     */
    private List<String> exposedPorts;

    /** list of regex names for pods to be logged **/
    private List<String> logPods;
}
