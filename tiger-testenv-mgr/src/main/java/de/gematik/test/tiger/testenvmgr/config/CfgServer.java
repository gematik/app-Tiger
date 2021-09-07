/*
 * Copyright (c) 2021 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CfgServer {
    private String name;
    private String template;
    private CfgProductType product;
    private String type;
    private List<String>  source= new ArrayList<>();
    private String version;
    private String workingDir;
    private List<String> options = new ArrayList<>();
    private List<String> arguments = new ArrayList<>();
    private Integer startupTimeoutSec;
    private String healthcheck;
    private List<String> serviceHealthchecks;
    private boolean active = true;
    private final List<CfgKey> pkiKeys = new ArrayList<>();
    private boolean proxied = true;
    private boolean oneShot = false;
    private String entryPoint;
    private final List<String> environment = new ArrayList<>();
    private final List<String> urlMappings = new ArrayList<>();
    private final List<String> exports = new ArrayList<>();
    private Map<Integer, Integer> ports;
}
